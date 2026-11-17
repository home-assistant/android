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
import io.homeassistant.companion.android.common.data.integration.EntityFilter
import io.homeassistant.companion.android.common.data.integration.display.EntitiesForDisplayManager
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayState
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayWithContext
import io.homeassistant.companion.android.common.data.prefs.AutoFavorite
import io.homeassistant.companion.android.common.data.prefs.PrefsRepository
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.database.server.Server
import io.homeassistant.companion.android.util.vehicle.VEHICLE_DOMAINS
import io.homeassistant.companion.android.util.vehicle.isVehicleDomain
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@RequiresApi(Build.VERSION_CODES.O)
@HiltViewModel
class ManageAndroidAutoViewModel @Inject constructor(
    private val serverManager: ServerManager,
    private val entitiesForDisplayManager: EntitiesForDisplayManager,
    private val prefsRepository: PrefsRepository,
    application: Application,
) : AndroidViewModel(application) {

    val favoritesList = mutableStateListOf<AutoFavorite>()

    var displayEntities by mutableStateOf<EntityDisplayState<EntityDisplayWithContext>>(EntityDisplayState.Loading)
        private set

    private val displayEntitiesByServer = mutableMapOf<Int, EntityDisplayState<EntityDisplayWithContext>>()

    var servers by mutableStateOf(emptyList<Server>())
        private set

    var defaultServerId by mutableIntStateOf(0)

    var isLoading by mutableStateOf(true)
        private set

    init {
        viewModelScope.launch {
            servers = serverManager.servers()
            defaultServerId = serverManager.getServer()?.id ?: 0
            favoritesList.addAll(prefsRepository.getAutoFavorites())
            servers.map { server ->
                val serverId = server.id
                async {
                    entitiesForDisplayManager
                        .snapshotInContext(serverId, EntityFilter(domains = VEHICLE_DOMAINS))
                        .map { state ->
                            state.keepingVehicleEntities()
                        }
                        .flowOn(Dispatchers.Default)
                        .collect { state -> displayEntitiesByServer[serverId] = state }
                }
            }.awaitAll()
            loadEntities(serverManager.getServer()?.id ?: 0)
            isLoading = false
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
        displayEntities = displayEntitiesByServer[serverId] ?: EntityDisplayState.Loading
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
}

/**
 * Keeps the entities usable in a vehicle: some of the vehicle domains only are when the entity has
 * coordinates, which the server can't filter on.
 */
private fun EntityDisplayState<EntityDisplayWithContext>.keepingVehicleEntities() = when (this) {
    is EntityDisplayState.Loaded -> EntityDisplayState.Loaded(entities.filter(::isVehicleDomain))
    EntityDisplayState.Loading, EntityDisplayState.Error -> this
}
