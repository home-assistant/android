package io.homeassistant.companion.android.settings.mediacontrol

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.common.compose.composable.HADropdownItem
import io.homeassistant.companion.android.common.data.integration.IntegrationDomains.MEDIA_PLAYER_DOMAIN
import io.homeassistant.companion.android.common.data.integration.display.EntitiesForDisplayManager
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayState
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayWithContext
import io.homeassistant.companion.android.common.data.mediacontrol.MediaControlEntityConfig
import io.homeassistant.companion.android.common.data.mediacontrol.MediaControlRepository
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.database.server.Server
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

/** One-shot events emitted by [MediaControlSettingsViewModel] for the UI layer to act on. */
sealed interface MediaControlServiceEvent {
    data object Start : MediaControlServiceEvent
}

/**
 * A configured media player entity paired with the display information of its entity, which is
 * null until the entities of [MediaControlEntityConfig.serverId] are resolved.
 *
 * [serverName] falls back to [MediaControlEntityConfig.serverId] when the server is not registered
 * anymore, so the row still identifies where the orphaned entity came from.
 */
data class MediaControlSelectedEntity(
    val config: MediaControlEntityConfig,
    val entityForDisplay: EntityDisplayWithContext?,
    val serverName: String,
)

@Stable
data class MediaControlSettingsUiState(
    val selectedServerId: Int = ServerManager.SERVER_ID_ACTIVE,
    val serversDropdownItems: List<HADropdownItem<Int>> = emptyList(),
    val mediaControlEntityConfigs: List<MediaControlEntityConfig> = emptyList(),
    val entityDisplayStatePerServer: Map<Int, EntityDisplayState<EntityDisplayWithContext>> = emptyMap(),
    val isLoading: Boolean = true,
) {
    /**
     * Media players of the selected server that are not configured yet, the choices of the entity
     * picker. Stays [EntityDisplayState.Loading] until that server resolved its entities, so the
     * picker shows its own loading indicator instead of disappearing.
     */
    val availableEntities: EntityDisplayState<EntityDisplayWithContext> =
        when (val displayState = entityDisplayStatePerServer[selectedServerId]) {
            null -> EntityDisplayState.Loading
            is EntityDisplayState.Loaded -> {
                val configured = mediaControlEntityConfigs
                    .filter { it.serverId == selectedServerId }
                    .mapTo(HashSet()) { it.entityId }
                displayState.copy(entitiesById = displayState.entitiesById.filterKeys { it !in configured })
            }
            else -> displayState
        }

    /** The configured entities, with the display information and the server name resolved. */
    val selectedEntities: List<MediaControlSelectedEntity> = mediaControlEntityConfigs.map { config ->
        MediaControlSelectedEntity(
            config = config,
            entityForDisplay = (entityDisplayStatePerServer[config.serverId] as? EntityDisplayState.Loaded)
                ?.entity(config.entityId),
            serverName = serversDropdownItems.find { it.key == config.serverId }?.label
                ?: config.serverId.toString(),
        )
    }
}

@HiltViewModel
class MediaControlSettingsViewModel @Inject constructor(
    private val serverManager: ServerManager,
    private val mediaControlRepository: MediaControlRepository,
    private val entitiesForDisplayManager: EntitiesForDisplayManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MediaControlSettingsUiState())
    val uiState: StateFlow<MediaControlSettingsUiState> = _uiState.asStateFlow()

    private val _serviceEvents = MutableSharedFlow<MediaControlServiceEvent>(extraBufferCapacity = 1)
    val serviceEvents: SharedFlow<MediaControlServiceEvent> = _serviceEvents.asSharedFlow()

    init {
        viewModelScope.launch { observeServers() }

        // Observe the DB-backed configured list; drives selectedEntities reactively
        viewModelScope.launch {
            mediaControlRepository.observeConfiguredEntities().collect { dbConfigs ->
                _uiState.update { state ->
                    state.copy(
                        mediaControlEntityConfigs = dbConfigs,
                        isLoading = false,
                    )
                }
                if (dbConfigs.isNotEmpty()) {
                    _serviceEvents.emit(MediaControlServiceEvent.Start)
                }
            }
        }
    }

    /** Updates the selected server in the entity picker. */
    fun selectServerId(serverId: Int) {
        _uiState.update { it.copy(selectedServerId = serverId) }
    }

    /**
     * Adds the entity identified by [entityId] from the currently selected server to the configured
     * list, then persists the change immediately. Has no effect if the entity is already in the list.
     */
    fun addEntity(entityId: String) {
        viewModelScope.launch {
            val state = _uiState.value
            // Resolves SERVER_ID_ACTIVE to the active server, so a config never stores that placeholder
            val serverId = serverManager.getServer(state.selectedServerId)?.id
            if (serverId == null) {
                Timber.w("Ignoring the entity to configure, server ${state.selectedServerId} is gone")
                return@launch
            }

            val config = MediaControlEntityConfig(serverId = serverId, entityId = entityId)
            if (config !in state.mediaControlEntityConfigs) {
                mediaControlRepository.setConfiguredEntities(state.mediaControlEntityConfigs + config)
            }
        }
    }

    /**
     * Removes [selectedEntity] from the configured list, then persists the change immediately.
     * Has no effect if it is not found in the list.
     */
    fun removeEntity(selectedEntity: MediaControlSelectedEntity) {
        viewModelScope.launch {
            val newConfigs = _uiState.value.mediaControlEntityConfigs.filterNot { it == selectedEntity.config }
            mediaControlRepository.setConfiguredEntities(newConfigs)
        }
    }

    /**
     * Follows the registered servers and resolves the media players of each of them into the state.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun observeServers() {
        val defaultServerId = serverManager.getServer()?.id ?: ServerManager.SERVER_ID_ACTIVE
        serverManager.serversFlow
            .onEach { servers -> publishServers(servers, defaultServerId) }
            // serversFlow re-emits on any write to the server table (a token refresh for instance),
            // resolving the entities again is only worth it when the servers themselves changed
            .map { servers -> servers.map { it.id } }
            .distinctUntilChanged()
            .flatMapLatest { serverIds -> serverIds.map { mediaPlayersOf(it) }.merge() }
            .collect()
    }

    /**
     * Publishes [servers] to the dropdown, selecting [defaultServerId] until the user picks another
     * server, and falling back to it when the selected server is gone.
     */
    private fun publishServers(servers: List<Server>, defaultServerId: Int) {
        _uiState.update { state ->
            state.copy(
                serversDropdownItems = servers.map { HADropdownItem(key = it.id, label = it.friendlyName) },
                selectedServerId = state.selectedServerId
                    .takeIf { id -> servers.any { it.id == id } }
                    ?: defaultServerId,
            )
        }
    }

    /** Resolves the media players of [serverId] into the state as they load. */
    private fun mediaPlayersOf(serverId: Int) = entitiesForDisplayManager
        .snapshotInContext(serverId) { it.domain == MEDIA_PLAYER_DOMAIN }
        .onEach { displayState ->
            _uiState.update { state ->
                state.copy(
                    entityDisplayStatePerServer = state.entityDisplayStatePerServer + (serverId to displayState),
                )
            }
        }
}
