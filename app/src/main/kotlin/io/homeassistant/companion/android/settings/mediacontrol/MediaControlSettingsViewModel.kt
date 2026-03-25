package io.homeassistant.companion.android.settings.mediacontrol

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.IntegrationDomains.MEDIA_PLAYER_DOMAIN
import io.homeassistant.companion.android.common.data.mediacontrol.MediaControlRepository
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.data.websocket.impl.entities.AreaRegistryResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.DeviceRegistryResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.EntityRegistryResponse
import io.homeassistant.companion.android.database.server.Server
import io.homeassistant.companion.android.mediacontrol.HaMediaSessionService
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

data class MediaControlSettingsUiState(
    val servers: List<Server> = emptyList(),
    val entities: List<Entity> = emptyList(),
    val entityRegistry: List<EntityRegistryResponse> = emptyList(),
    val deviceRegistry: List<DeviceRegistryResponse> = emptyList(),
    val areaRegistry: List<AreaRegistryResponse> = emptyList(),
    val selectedServerId: Int = ServerManager.SERVER_ID_ACTIVE,
    val selectedEntityId: String = "",
    val isConfigured: Boolean = false,
)

@HiltViewModel
class MediaControlSettingsViewModel @Inject constructor(
    private val serverManager: ServerManager,
    private val mediaControlRepository: MediaControlRepository,
    application: Application,
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(MediaControlSettingsUiState())
    val uiState: StateFlow<MediaControlSettingsUiState> = _uiState.asStateFlow()

    private val entities = mutableMapOf<Int, List<Entity>>()
    private val entityRegistries = mutableMapOf<Int, List<EntityRegistryResponse>>()
    private val deviceRegistries = mutableMapOf<Int, List<DeviceRegistryResponse>>()
    private val areaRegistries = mutableMapOf<Int, List<AreaRegistryResponse>>()

    private data class ServerRegistries(
        val serverId: Int,
        val entities: List<Entity>,
        val entityRegistry: List<EntityRegistryResponse>,
        val deviceRegistry: List<DeviceRegistryResponse>,
        val areaRegistry: List<AreaRegistryResponse>,
    )

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val loadedServers = serverManager.servers()
            _uiState.update { it.copy(servers = loadedServers) }
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
                entities[registries.serverId] = registries.entities
                entityRegistries[registries.serverId] = registries.entityRegistry
                deviceRegistries[registries.serverId] = registries.deviceRegistry
                areaRegistries[registries.serverId] = registries.areaRegistry
            }

            val configuredServerId = mediaControlRepository.getConfiguredServerId()
            val configuredEntityId = mediaControlRepository.getConfiguredEntityId()

            if (configuredServerId != null && configuredEntityId != null) {
                _uiState.update {
                    it.copy(
                        selectedServerId = configuredServerId,
                        selectedEntityId = configuredEntityId,
                        isConfigured = true,
                    )
                }
            } else {
                _uiState.update {
                    it.copy(selectedServerId = serverManager.getServer()?.id ?: 0)
                }
            }
            loadEntities(_uiState.value.selectedServerId)
        }
    }

    fun selectServerId(serverId: Int) {
        if (serverId != _uiState.value.selectedServerId) {
            _uiState.update {
                it.copy(selectedServerId = serverId, selectedEntityId = "")
            }
            loadEntities(serverId)
        }
    }

    fun selectEntityId(entityId: String) {
        _uiState.update { it.copy(selectedEntityId = entityId) }
    }

    /** Saves the current entity selection and starts the media session service. */
    fun saveConfiguration() {
        viewModelScope.launch {
            val state = _uiState.value
            mediaControlRepository.setConfiguredEntity(
                serverId = state.selectedServerId,
                entityId = state.selectedEntityId,
            )
            _uiState.update { it.copy(isConfigured = true) }
            startService()
        }
    }

    /** Clears the configuration and stops the media session service. */
    fun clearConfiguration() {
        viewModelScope.launch {
            mediaControlRepository.setConfiguredEntity(serverId = null, entityId = null)
            _uiState.update { it.copy(selectedEntityId = "", isConfigured = false) }
            stopService()
        }
    }

    private fun loadEntities(serverId: Int) {
        _uiState.update {
            it.copy(
                entities = entities[serverId] ?: emptyList(),
                entityRegistry = entityRegistries[serverId] ?: emptyList(),
                deviceRegistry = deviceRegistries[serverId] ?: emptyList(),
                areaRegistry = areaRegistries[serverId] ?: emptyList(),
            )
        }
    }

    private fun startService() {
        val context = getApplication<Application>()
        val intent = Intent(context, HaMediaSessionService::class.java)
        intent.action = HaMediaSessionService.ACTION_RESTART_OBSERVATION
        context.startService(intent)
    }

    private fun stopService() {
        val context = getApplication<Application>()
        val intent = Intent(context, HaMediaSessionService::class.java)
        context.stopService(intent)
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
