package io.homeassistant.companion.android.settings.mediacontrol

import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.IntegrationDomains.MEDIA_PLAYER_DOMAIN
import io.homeassistant.companion.android.common.data.mediacontrol.MediaControlEntityConfig
import io.homeassistant.companion.android.common.data.mediacontrol.MediaControlRepository
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.data.websocket.impl.entities.AreaRegistryResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.DeviceRegistryResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.EntityRegistryResponse
import io.homeassistant.companion.android.database.server.Server
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
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
    // Server selection for the entity picker
    val selectedServerId: Int = ServerManager.SERVER_ID_ACTIVE,
    // True while entities and registries are being loaded from the server
    val isLoading: Boolean = true,
)

@HiltViewModel
class MediaControlSettingsViewModel @Inject constructor(
    private val serverManager: ServerManager,
    private val mediaControlRepository: MediaControlRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MediaControlSettingsUiState())
    val uiState: StateFlow<MediaControlSettingsUiState> = _uiState.asStateFlow()

    private val _serviceEvents = MutableSharedFlow<MediaControlServiceEvent>(extraBufferCapacity = 1)
    val serviceEvents: SharedFlow<MediaControlServiceEvent> = _serviceEvents.asSharedFlow()

    private val entitiesPerServer = ConcurrentHashMap<Int, List<Entity>>()
    private val entityRegistriesPerServer = ConcurrentHashMap<Int, List<EntityRegistryResponse>>()
    private val deviceRegistriesPerServer = ConcurrentHashMap<Int, List<DeviceRegistryResponse>>()
    private val areaRegistriesPerServer = ConcurrentHashMap<Int, List<AreaRegistryResponse>>()

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
            results.forEach { registries ->
                entitiesPerServer[registries.serverId] = registries.entities
                entityRegistriesPerServer[registries.serverId] = registries.entityRegistry
                deviceRegistriesPerServer[registries.serverId] = registries.deviceRegistry
                areaRegistriesPerServer[registries.serverId] = registries.areaRegistry
            }
            _uiState.update { state ->
                state.copy(
                    entitiesPerServer = entitiesPerServer.toMap(),
                    entityRegistryPerServer = entityRegistriesPerServer.toMap(),
                    deviceRegistryPerServer = deviceRegistriesPerServer.toMap(),
                    areaRegistryPerServer = areaRegistriesPerServer.toMap(),
                    isLoading = false,
                )
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
        val config = MediaControlEntityConfig(
            serverId = _uiState.value.selectedServerId,
            entityId = entityId,
        )
        _uiState.update { state ->
            if (state.configuredEntities.contains(config)) {
                state
            } else {
                state.copy(configuredEntities = state.configuredEntities + config)
            }
        }
        persistAndNotifyService()
    }

    /** Removes the configured entity at [index] from the list, then persists the change immediately. */
    fun removeEntity(index: Int) {
        _uiState.update { state ->
            state.copy(configuredEntities = state.configuredEntities.toMutableList().also { it.removeAt(index) })
        }
        persistAndNotifyService()
    }

    /**
     * Moves a configured entity from one position to another in response to a drag gesture.
     * Does not persist — call [onReorderComplete] once the drag ends to save the final order.
     */
    fun onMove(from: LazyListItemInfo, to: LazyListItemInfo) {
        _uiState.update { state ->
            val list = state.configuredEntities.toMutableList()
            val fromIndex = list.indexOfFirst { it == from.key }
            val toIndex = list.indexOfFirst { it == to.key }
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

    private suspend fun loadMediaPlayerEntities(serverId: Int): List<Entity> = try {
        serverManager.integrationRepository(serverId).getEntities().orEmpty()
            .filter { it.domain == MEDIA_PLAYER_DOMAIN }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Timber.e(e, "Couldn't load media_player entities for server")
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
