package io.homeassistant.companion.android.settings.mediacontrol

import android.app.Application
import androidx.lifecycle.AndroidViewModel
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
    data object Stop : MediaControlServiceEvent
}

data class MediaControlSettingsUiState(
    val servers: List<Server> = emptyList(),
    // All loaded entities/registries per server, used by the entity picker
    val entitiesPerServer: Map<Int, List<Entity>> = emptyMap(),
    val entityRegistryPerServer: Map<Int, List<EntityRegistryResponse>> = emptyMap(),
    val deviceRegistryPerServer: Map<Int, List<DeviceRegistryResponse>> = emptyMap(),
    val areaRegistryPerServer: Map<Int, List<AreaRegistryResponse>> = emptyMap(),
    // The in-memory list of entities being configured
    val configuredEntities: List<MediaControlEntityConfig> = emptyList(),
    // Whether the "add entity" inline form is currently shown
    val showAddSlot: Boolean = false,
    // Server selection within the pending add form
    val pendingServerId: Int = ServerManager.SERVER_ID_ACTIVE,
)

@HiltViewModel
class MediaControlSettingsViewModel @Inject constructor(
    private val serverManager: ServerManager,
    private val mediaControlRepository: MediaControlRepository,
    application: Application,
) : AndroidViewModel(application) {

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
            _uiState.update { it.copy(servers = loadedServers, pendingServerId = defaultServerId) }

            val results = loadedServers.map { server ->
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
            }.awaitAll()
            results.forEach { registries ->
                entitiesPerServer[registries.serverId] = registries.entities
                entityRegistriesPerServer[registries.serverId] = registries.entityRegistry
                deviceRegistriesPerServer[registries.serverId] = registries.deviceRegistry
                areaRegistriesPerServer[registries.serverId] = registries.areaRegistry
            }

            val configuredEntities = mediaControlRepository.getConfiguredEntities()
            _uiState.update { state ->
                state.copy(
                    entitiesPerServer = entitiesPerServer.toMap(),
                    entityRegistryPerServer = entityRegistriesPerServer.toMap(),
                    deviceRegistryPerServer = deviceRegistriesPerServer.toMap(),
                    areaRegistryPerServer = areaRegistriesPerServer.toMap(),
                    configuredEntities = configuredEntities,
                )
            }
        }
    }

    /** Opens the inline form to add a new entity slot. */
    fun showAddEntity() {
        val defaultServerId = _uiState.value.let { state ->
            if (state.servers.isNotEmpty()) state.servers.first().id else ServerManager.SERVER_ID_ACTIVE
        }
        _uiState.update { it.copy(showAddSlot = true, pendingServerId = defaultServerId) }
    }

    /** Cancels the pending add-entity form without making changes. */
    fun cancelAddEntity() {
        _uiState.update { it.copy(showAddSlot = false) }
    }

    /** Updates the selected server in the pending add form, resetting entity selection. */
    fun selectPendingServerId(serverId: Int) {
        _uiState.update { it.copy(pendingServerId = serverId) }
    }

    /**
     * Adds the entity identified by [entityId] from the pending server to the configured list,
     * then hides the add form. Has no effect if the entity is already in the list.
     */
    fun addPendingEntity(entityId: String) {
        val config = MediaControlEntityConfig(
            serverId = _uiState.value.pendingServerId,
            entityId = entityId,
        )
        _uiState.update { state ->
            if (state.configuredEntities.contains(config)) {
                state.copy(showAddSlot = false)
            } else {
                state.copy(
                    configuredEntities = state.configuredEntities + config,
                    showAddSlot = false,
                )
            }
        }
    }

    /** Removes the configured entity at [index] from the list. */
    fun removeEntity(index: Int) {
        _uiState.update { state ->
            state.copy(configuredEntities = state.configuredEntities.toMutableList().also { it.removeAt(index) })
        }
    }

    /** Saves the current list of configured entities and emits a service event to the UI layer. */
    fun saveConfiguration() {
        viewModelScope.launch {
            val entities = _uiState.value.configuredEntities
            mediaControlRepository.setConfiguredEntities(entities)
            _serviceEvents.emit(
                if (entities.isEmpty()) MediaControlServiceEvent.Stop else MediaControlServiceEvent.Start,
            )
        }
    }

    /** Clears all configured entities and emits a stop event to the UI layer. */
    fun clearAllConfiguration() {
        viewModelScope.launch {
            mediaControlRepository.setConfiguredEntities(emptyList())
            _uiState.update { it.copy(configuredEntities = emptyList(), showAddSlot = false) }
            _serviceEvents.emit(MediaControlServiceEvent.Stop)
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
