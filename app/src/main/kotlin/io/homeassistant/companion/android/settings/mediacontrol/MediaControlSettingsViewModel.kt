package io.homeassistant.companion.android.settings.mediacontrol

import android.app.Application
import androidx.compose.runtime.Stable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mikepenz.iconics.typeface.IIcon
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.IntegrationDomains.MEDIA_PLAYER_DOMAIN
import io.homeassistant.companion.android.common.data.integration.friendlyName
import io.homeassistant.companion.android.common.data.integration.getIcon
import io.homeassistant.companion.android.common.data.mediacontrol.MediaControlEntityConfig
import io.homeassistant.companion.android.common.data.mediacontrol.MediaControlRepository
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.data.websocket.impl.entities.AreaRegistryResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.DeviceRegistryResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.EntityRegistryResponse
import io.homeassistant.companion.android.database.server.Server
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
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

@Stable
data class MediaControlSettingsUiState(
    val servers: List<Server> = emptyList(),
    // All loaded entities/registries per server, used by the entity picker
    val entitiesPerServer: Map<Int, List<Entity>> = emptyMap(),
    val entityRegistryPerServer: Map<Int, List<EntityRegistryResponse>> = emptyMap(),
    val deviceRegistryPerServer: Map<Int, List<DeviceRegistryResponse>> = emptyMap(),
    val areaRegistryPerServer: Map<Int, List<AreaRegistryResponse>> = emptyMap(),
    // The in-memory list of entities being configured
    val configuredEntities: List<MediaControlEntityConfig> = emptyList(),
    // Precomputed friendly names for each configured entity; absent means not yet loaded
    val entityNamesByConfig: Map<MediaControlEntityConfig, String> = emptyMap(),
    // Precomputed icons for each configured entity; absent means not yet loaded
    val entityIconsByConfig: Map<MediaControlEntityConfig, IIcon> = emptyMap(),
    // Entities for the selected server that are not yet configured, ready for the picker
    val availableEntities: List<Entity> = emptyList(),
    // Server selection for the entity picker
    val selectedServerId: Int = ServerManager.SERVER_ID_ACTIVE,
    // True while entities and registries are being loaded from the server
    val isLoading: Boolean = true,
) {
    fun entityRegistryForServer(serverId: Int): List<EntityRegistryResponse> = entityRegistryPerServer[serverId] ?: emptyList()
    fun deviceRegistryForServer(serverId: Int): List<DeviceRegistryResponse> = deviceRegistryPerServer[serverId] ?: emptyList()
    fun areaRegistryForServer(serverId: Int): List<AreaRegistryResponse> = areaRegistryPerServer[serverId] ?: emptyList()
}

@HiltViewModel
class MediaControlSettingsViewModel @Inject constructor(
    application: Application,
    private val serverManager: ServerManager,
    private val mediaControlRepository: MediaControlRepository,
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(MediaControlSettingsUiState())
    val uiState: StateFlow<MediaControlSettingsUiState> = _uiState.asStateFlow()

    private val _serviceEvents = MutableSharedFlow<MediaControlServiceEvent>(extraBufferCapacity = 1)
    val serviceEvents: SharedFlow<MediaControlServiceEvent> = _serviceEvents.asSharedFlow()

    private data class ServerRegistries(
        val serverId: Int,
        val entities: List<Entity>,
        val entityRegistry: List<EntityRegistryResponse>,
        val deviceRegistry: List<DeviceRegistryResponse>,
        val areaRegistry: List<AreaRegistryResponse>,
    )

    init {
        viewModelScope.launch {
            val loadedServers = serverManager.servers()
            val defaultServerId = serverManager.getServer()?.id ?: ServerManager.SERVER_ID_ACTIVE

            // Load configured entities (local DB) and server registries (network) concurrently.
            // Emit the configured list as soon as the DB read completes so the list appears immediately.
            val configuredEntitiesDeferred = async { mediaControlRepository.getConfiguredEntities() }
            val registryResults = loadedServers.map { server ->
                async {
                    ServerRegistries(
                        serverId = server.id,
                        entities = loadMediaPlayerEntities(server.id),
                        entityRegistry = loadRegistry(server.id, "entity registry") {
                            serverManager.webSocketRepository(it).getEntityRegistry()
                        },
                        deviceRegistry = loadRegistry(server.id, "device registry") {
                            serverManager.webSocketRepository(it).getDeviceRegistry()
                        },
                        areaRegistry = loadRegistry(server.id, "area registry") {
                            serverManager.webSocketRepository(it).getAreaRegistry()
                        },
                    )
                }
            }

            val configuredEntities = configuredEntitiesDeferred.await()
            _uiState.update {
                it.copy(
                    servers = loadedServers,
                    selectedServerId = defaultServerId,
                    configuredEntities = configuredEntities,
                )
            }

            val results = registryResults.awaitAll()
            _uiState.update { state ->
                val entitiesPerServer = results.associate { it.serverId to it.entities }
                state.copy(
                    entitiesPerServer = entitiesPerServer,
                    entityRegistryPerServer = results.associate { it.serverId to it.entityRegistry },
                    deviceRegistryPerServer = results.associate { it.serverId to it.deviceRegistry },
                    areaRegistryPerServer = results.associate { it.serverId to it.areaRegistry },
                    entityNamesByConfig = buildEntityNamesByConfig(entitiesPerServer, state.configuredEntities),
                    entityIconsByConfig = buildEntityIconsByConfig(entitiesPerServer, state.configuredEntities),
                    isLoading = false,
                )
            }
            updateAvailableEntities()
        }
    }

    /** Updates the selected server in the entity picker. */
    fun selectServerId(serverId: Int) {
        _uiState.update { it.copy(selectedServerId = serverId) }
        updateAvailableEntities()
    }

    /**
     * Adds the entity identified by [entityId] from the currently selected server to the configured
     * list, then persists the change immediately. Has no effect if the entity is already in the list.
     */
    fun addEntity(entityId: String) {
        _uiState.update { state ->
            val config = MediaControlEntityConfig(
                serverId = state.selectedServerId,
                entityId = entityId,
            )
            if (state.configuredEntities.contains(config)) {
                state
            } else {
                val newConfiguredEntities = state.configuredEntities + config
                state.copy(
                    configuredEntities = newConfiguredEntities,
                    entityNamesByConfig = buildEntityNamesByConfig(state.entitiesPerServer, newConfiguredEntities),
                    entityIconsByConfig = buildEntityIconsByConfig(state.entitiesPerServer, newConfiguredEntities),
                )
            }
        }
        updateAvailableEntities()
        persistAndNotifyService()
    }

    /** Removes the configured entity at [index] from the list, then persists the change immediately. */
    fun removeEntity(index: Int) {
        _uiState.update { state ->
            val newConfiguredEntities = state.configuredEntities.toMutableList().also { it.removeAt(index) }
            state.copy(
                configuredEntities = newConfiguredEntities,
                entityNamesByConfig = buildEntityNamesByConfig(state.entitiesPerServer, newConfiguredEntities),
                entityIconsByConfig = buildEntityIconsByConfig(state.entitiesPerServer, newConfiguredEntities),
            )
        }
        updateAvailableEntities()
        persistAndNotifyService()
    }

    /**
     * Moves a configured entity from one position to another in response to a drag gesture.
     * Does not persist — call [onReorderComplete] once the drag ends to save the final order.
     * @param fromKey the list item key of the entity being dragged
     * @param toKey the list item key of the target position
     */
    fun onMove(fromKey: Any, toKey: Any) {
        _uiState.update { state ->
            val list = state.configuredEntities.toMutableList()
            val fromIndex = list.indexOfFirst { it == fromKey }
            val toIndex = list.indexOfFirst { it == toKey }
            if (fromIndex >= 0 && toIndex >= 0) {
                list.add(toIndex, list.removeAt(fromIndex))
            }
            state.copy(configuredEntities = list)
        }
    }

    /** Persists the current entity order after a drag-to-reorder gesture completes. */
    fun onReorderComplete() {
        persistAndNotifyService()
    }

    private fun persistAndNotifyService() {
        viewModelScope.launch {
            val entities = _uiState.value.configuredEntities
            mediaControlRepository.setConfiguredEntities(entities)
            if (entities.isNotEmpty()) {
                _serviceEvents.emit(MediaControlServiceEvent.Start)
            }
        }
    }

    private fun updateAvailableEntities() {
        viewModelScope.launch(Dispatchers.Default) {
            _uiState.update { state ->
                val configuredForServer = state.configuredEntities
                    .filter { it.serverId == state.selectedServerId }
                    .mapTo(HashSet()) { it.entityId }
                state.copy(
                    availableEntities = (state.entitiesPerServer[state.selectedServerId] ?: emptyList())
                        .filter { it.entityId !in configuredForServer },
                )
            }
        }
    }

    private fun buildEntityIconsByConfig(
        entitiesPerServer: Map<Int, List<Entity>>,
        configuredEntities: List<MediaControlEntityConfig>,
    ): Map<MediaControlEntityConfig, IIcon> = configuredEntities.mapNotNull { config ->
        val icon = entitiesPerServer[config.serverId]
            ?.firstOrNull { it.entityId == config.entityId }
            ?.getIcon(getApplication())
            ?: return@mapNotNull null
        config to icon
    }.toMap()

    private fun buildEntityNamesByConfig(
        entitiesPerServer: Map<Int, List<Entity>>,
        configuredEntities: List<MediaControlEntityConfig>,
    ): Map<MediaControlEntityConfig, String> = configuredEntities.mapNotNull { config ->
        val name = entitiesPerServer[config.serverId]
            ?.firstOrNull { it.entityId == config.entityId }
            ?.friendlyName
            ?: return@mapNotNull null
        config to name
    }.toMap()

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
            Timber.e(e, "Couldn't load $name for server")
            emptyList()
        }
}
