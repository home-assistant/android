package io.homeassistant.companion.android.settings.vehicle

import android.app.Application
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.prefs.AutoFavorite
import io.homeassistant.companion.android.common.data.prefs.PrefsRepository
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.data.websocket.impl.entities.AreaRegistryResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.DeviceRegistryResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.EntityRegistryResponse
import io.homeassistant.companion.android.database.server.Server
import io.homeassistant.companion.android.util.vehicle.isVehicleDomain
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import timber.log.Timber

@RequiresApi(Build.VERSION_CODES.O)
@HiltViewModel
class ManageAndroidAutoViewModel @Inject constructor(
    private val serverManager: ServerManager,
    private val prefsRepository: PrefsRepository,
    application: Application,
) : AndroidViewModel(application) {

    val favoritesList = mutableStateListOf<AutoFavorite>()

    var sortedEntities by mutableStateOf<List<Entity>>(emptyList())
        private set
    var entityRegistry by mutableStateOf<List<EntityRegistryResponse>>(emptyList())
        private set
    var deviceRegistry by mutableStateOf<List<DeviceRegistryResponse>>(emptyList())
        private set
    var areaRegistry by mutableStateOf<List<AreaRegistryResponse>>(emptyList())
        private set
    private val entities = mutableMapOf<Int, List<Entity>>()
    private val entityRegistries = mutableMapOf<Int, List<EntityRegistryResponse>>()
    private val deviceRegistries = mutableMapOf<Int, List<DeviceRegistryResponse>>()
    private val areaRegistries = mutableMapOf<Int, List<AreaRegistryResponse>>()

    var servers by mutableStateOf(emptyList<Server>())
        private set

    var defaultServerId by mutableIntStateOf(0)

    init {
        viewModelScope.launch {
            servers = serverManager.servers()
            defaultServerId = serverManager.getServer()?.id ?: 0
            favoritesList.addAll(prefsRepository.getAutoFavorites())
            servers.map { server ->
                val serverId = server.id
                async {
                    launch { entities[serverId] = loadEntitiesForServer(serverId) }
                    launch { entityRegistries[serverId] = loadEntityRegistry(serverId) }
                    launch { deviceRegistries[serverId] = loadDeviceRegistry(serverId) }
                    launch { areaRegistries[serverId] = loadAreaRegistry(serverId) }
                }
            }.awaitAll()
            loadEntities(serverManager.getServer()?.id ?: 0)
        }
    }

    fun onMove(fromItem: LazyListItemInfo, toItem: LazyListItemInfo) {
        favoritesList.apply {
            add(
                favoritesList.indexOfFirst { it == toItem.key },
                removeAt(favoritesList.indexOfFirst { it == fromItem.key }),
            )
        }
    }

    fun saveFavorites() {
        viewModelScope.launch {
            prefsRepository.setAutoFavorites(favoritesList.toList())
        }
    }

    fun loadEntities(serverId: Int) {
        sortedEntities = entities[serverId] ?: emptyList()
        entityRegistry = entityRegistries[serverId] ?: emptyList()
        deviceRegistry = deviceRegistries[serverId] ?: emptyList()
        areaRegistry = areaRegistries[serverId] ?: emptyList()
    }

    fun onEntitySelected(checked: Boolean, entityId: String, serverId: Int) {
        val favorite = AutoFavorite(serverId, entityId)
        if (checked) {
            favoritesList.add(favorite)
        } else {
            favoritesList.remove(favorite)
        }
        viewModelScope.launch { prefsRepository.setAutoFavorites(favoritesList.toList()) }
    }

    private suspend fun loadEntitiesForServer(serverId: Int): List<Entity> = try {
        serverManager.integrationRepository(serverId).getEntities().orEmpty()
            .filter { isVehicleDomain(it) }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Timber.e(e, "Couldn't load entities for server")
        emptyList()
    }

    private suspend fun loadEntityRegistry(serverId: Int): List<EntityRegistryResponse> = try {
        serverManager.webSocketRepository(serverId).getEntityRegistry().orEmpty()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Timber.e(e, "Couldn't load entity registry for server")
        emptyList()
    }

    private suspend fun loadDeviceRegistry(serverId: Int): List<DeviceRegistryResponse> = try {
        serverManager.webSocketRepository(serverId).getDeviceRegistry().orEmpty()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Timber.e(e, "Couldn't load device registry for server")
        emptyList()
    }

    private suspend fun loadAreaRegistry(serverId: Int): List<AreaRegistryResponse> = try {
        serverManager.webSocketRepository(serverId).getAreaRegistry().orEmpty()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Timber.e(e, "Couldn't load area registry for server")
        emptyList()
    }
}
