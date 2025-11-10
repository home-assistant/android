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
import io.homeassistant.companion.android.util.vehicle.isVehicleDomain
import javax.inject.Inject
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
    val entities = mutableMapOf<Int, List<Entity>>()

    val defaultServers = serverManager.defaultServers

    var defaultServerId by mutableIntStateOf(0)

    init {
        viewModelScope.launch {
            defaultServerId = serverManager.getServer()?.id ?: 0
            favoritesList.addAll(prefsRepository.getAutoFavorites())
            serverManager.defaultServers.map {
                async {
                    entities[it.id] = try {
                        serverManager.integrationRepository(it.id).getEntities().orEmpty()
                            .filter {
                                isVehicleDomain(it)
                            }
                    } catch (e: Exception) {
                        Timber.e(e, "Couldn't load entities for server")
                        emptyList()
                    }
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
