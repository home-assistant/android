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
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayState
import io.homeassistant.companion.android.common.data.integration.display.GetEntitiesForDisplayUseCase
import io.homeassistant.companion.android.common.data.prefs.AutoFavorite
import io.homeassistant.companion.android.common.data.prefs.PrefsRepository
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.database.server.Server
import io.homeassistant.companion.android.util.vehicle.isVehicleDomain
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch

@RequiresApi(Build.VERSION_CODES.O)
@HiltViewModel
class ManageAndroidAutoViewModel @Inject constructor(
    private val serverManager: ServerManager,
    private val getEntitiesForDisplay: GetEntitiesForDisplayUseCase,
    private val prefsRepository: PrefsRepository,
    application: Application,
) : AndroidViewModel(application) {

    val favoritesList = mutableStateListOf<AutoFavorite>()

    var displayEntities by mutableStateOf<EntityDisplayState>(EntityDisplayState.Loading)
        private set

    private val displayEntitiesByServer = mutableMapOf<Int, EntityDisplayState>()

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
                    getEntitiesForDisplay(serverId) { isVehicleDomain(it) }.collect { state ->
                        displayEntitiesByServer[serverId] = state
                    }
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
