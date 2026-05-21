package io.homeassistant.companion.android.settings.mediacontrol

import androidx.annotation.VisibleForTesting
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.IntegrationDomains.MEDIA_PLAYER_DOMAIN
import io.homeassistant.companion.android.common.data.integration.friendlyName
import io.homeassistant.companion.android.common.data.mediacontrol.MediaControlEntityConfig
import io.homeassistant.companion.android.common.data.mediacontrol.MediaControlRepository
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.data.websocket.impl.entities.AreaRegistryResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.DeviceRegistryResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.EntityRegistryResponse
import io.homeassistant.companion.android.database.server.Server
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

/** One-shot events emitted by [MediaControlSettingsViewModel] for the UI layer to act on. */
sealed interface MediaControlServiceEvent {
    data object Start : MediaControlServiceEvent
}

/**
 * A configured media player entity paired with its resolved display name and entity data.
 * [name] always has a value — it falls back to [MediaControlEntityConfig.entityId] if [entity]
 * has not yet been loaded from the server. [entity] is null until server data is available;
 * the Compose layer uses it to resolve the entity icon via [LocalContext].
 */
data class ConfiguredEntityItem(val config: MediaControlEntityConfig, val name: String, val entity: Entity?)

@Stable
data class MediaControlSettingsUiState(
    val servers: List<Server> = emptyList(),
    // All loaded entities/registries per server, used by the entity picker
    val entitiesPerServer: Map<Int, List<Entity>> = emptyMap(),
    val entityRegistryPerServer: Map<Int, List<EntityRegistryResponse>> = emptyMap(),
    val deviceRegistryPerServer: Map<Int, List<DeviceRegistryResponse>> = emptyMap(),
    val areaRegistryPerServer: Map<Int, List<AreaRegistryResponse>> = emptyMap(),
    // The configured entities, with names and entity data resolved from server data
    val configuredEntityItems: List<ConfiguredEntityItem> = emptyList(),
    // Server selection for the entity picker
    val selectedServerId: Int = ServerManager.SERVER_ID_ACTIVE,
    // True while entities and registries are being loaded from the server
    val isLoading: Boolean = true,
) {
    /** Entities for the selected server that are not yet configured, ready for the entity picker. */
    val availableEntities: List<Entity>
        get() {
            val configuredForServer = configuredEntityItems
                .filter { it.config.serverId == selectedServerId }
                .mapTo(HashSet()) { it.config.entityId }
            return (entitiesPerServer[selectedServerId] ?: emptyList())
                .filter { it.entityId !in configuredForServer }
        }

    fun entityRegistryForServer(serverId: Int): List<EntityRegistryResponse> =
        entityRegistryPerServer[serverId] ?: emptyList()
    fun deviceRegistryForServer(serverId: Int): List<DeviceRegistryResponse> =
        deviceRegistryPerServer[serverId] ?: emptyList()
    fun areaRegistryForServer(serverId: Int): List<AreaRegistryResponse> =
        areaRegistryPerServer[serverId] ?: emptyList()
}

@HiltViewModel
class MediaControlSettingsViewModel @VisibleForTesting constructor(
    private val serverManager: ServerManager,
    private val mediaControlRepository: MediaControlRepository,
    private val backgroundDispatcher: CoroutineDispatcher,
) : ViewModel() {

    @Inject
    constructor(
        serverManager: ServerManager,
        mediaControlRepository: MediaControlRepository,
    ) : this(serverManager, mediaControlRepository, Dispatchers.Default)

    private val _uiState = MutableStateFlow(MediaControlSettingsUiState())
    val uiState: StateFlow<MediaControlSettingsUiState> = _uiState.asStateFlow()

    private val _serviceEvents = MutableSharedFlow<MediaControlServiceEvent>(extraBufferCapacity = 1)
    val serviceEvents: SharedFlow<MediaControlServiceEvent> = _serviceEvents.asSharedFlow()

    init {
        // Coroutine 1: load server data (entities + registries) from the network
        viewModelScope.launch(backgroundDispatcher) {
            val loadedServers = serverManager.servers()
            val defaultServerId = serverManager.getServer()?.id ?: ServerManager.SERVER_ID_ACTIVE
            _uiState.update { it.copy(servers = loadedServers, selectedServerId = defaultServerId) }

            val entitiesDeferred = loadedServers.map { server ->
                async { server.id to loadMediaPlayerEntities(server.id) }
            }
            val entityRegistryDeferred = loadedServers.map { server ->
                async {
                    server.id to loadRegistry(server.id, "entity registry") {
                        serverManager.webSocketRepository(it).getEntityRegistry()
                    }
                }
            }
            val deviceRegistryDeferred = loadedServers.map { server ->
                async {
                    server.id to loadRegistry(server.id, "device registry") {
                        serverManager.webSocketRepository(it).getDeviceRegistry()
                    }
                }
            }
            val areaRegistryDeferred = loadedServers.map { server ->
                async {
                    server.id to loadRegistry(server.id, "area registry") {
                        serverManager.webSocketRepository(it).getAreaRegistry()
                    }
                }
            }

            val entitiesPerServer = entitiesDeferred.awaitAll().toMap()
            _uiState.update { state ->
                state.copy(
                    entitiesPerServer = entitiesPerServer,
                    entityRegistryPerServer = entityRegistryDeferred.awaitAll().toMap(),
                    deviceRegistryPerServer = deviceRegistryDeferred.awaitAll().toMap(),
                    areaRegistryPerServer = areaRegistryDeferred.awaitAll().toMap(),
                    // Re-resolve items now that entity names and data are available
                    configuredEntityItems = buildConfiguredItems(
                        entitiesPerServer,
                        state.configuredEntityItems.map { it.config },
                    ),
                    isLoading = false,
                )
            }
        }

        // Coroutine 2: observe the DB-backed configured list; drives configuredEntityItems reactively
        viewModelScope.launch {
            mediaControlRepository.observeConfiguredEntities().collect { dbConfigs ->
                _uiState.update { state ->
                    state.copy(
                        configuredEntityItems = buildConfiguredItems(state.entitiesPerServer, dbConfigs),
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
            val config = MediaControlEntityConfig(
                serverId = state.selectedServerId,
                entityId = entityId,
            )
            if (state.configuredEntityItems.none { it.config == config }) {
                val newConfigs = state.configuredEntityItems.map { it.config } + config
                mediaControlRepository.setConfiguredEntities(newConfigs)
            }
        }
    }

    /** Removes the configured entity at [index] from the list, then persists the change immediately. */
    fun removeEntity(index: Int) {
        viewModelScope.launch {
            val newConfigs = _uiState.value.configuredEntityItems
                .map { it.config }
                .toMutableList()
                .also { it.removeAt(index) }
            mediaControlRepository.setConfiguredEntities(newConfigs)
        }
    }

    private fun buildConfiguredItems(
        entitiesPerServer: Map<Int, List<Entity>>,
        configs: List<MediaControlEntityConfig>,
    ): List<ConfiguredEntityItem> = configs.map { config ->
        val entity = entitiesPerServer[config.serverId]?.firstOrNull { it.entityId == config.entityId }
        ConfiguredEntityItem(
            config = config,
            name = entity?.friendlyName ?: config.entityId,
            entity = entity,
        )
    }

    private suspend fun loadMediaPlayerEntities(serverId: Int): List<Entity> = try {
        serverManager.integrationRepository(serverId).getEntities().orEmpty()
            .filter { it.domain == MEDIA_PLAYER_DOMAIN }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Timber.e(e, "Couldn't load media_player entities for server $serverId")
        emptyList()
    }

    private suspend fun <T> loadRegistry(serverId: Int, name: String, loader: suspend (Int) -> List<T>?): List<T> =
        try {
            loader(serverId).orEmpty()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Couldn't load $name for server $serverId")
            emptyList()
        }
}
