package io.homeassistant.companion.android.settings.vehicle

import android.app.Application
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.prefs.PrefsRepository
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.util.vehicle.isVehicleDomain
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import org.burnoutcrew.reorderable.ItemPosition
import javax.inject.Inject

@RequiresApi(Build.VERSION_CODES.O)
@HiltViewModel
class ManageAndroidAutoViewModel @Inject constructor(
    private val serverManager: ServerManager,
    private val prefsRepository: PrefsRepository,
    application: Application
) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "AAViewModel"
    }
    val favoritesList = mutableStateListOf<String>()

    var sortedEntities by mutableStateOf<List<Entity<*>>>(emptyList())
        private set
    val entities = mutableMapOf<Int, List<Entity<*>>>()
    init {
        viewModelScope.launch {
            favoritesList.addAll(prefsRepository.getAutoFavorites())
            serverManager.defaultServers.map {
                async {
                    entities[it.id] = try {
                        serverManager.integrationRepository(it.id).getEntities().orEmpty()
                            .filter {
                                isVehicleDomain(it)
                            }
                    } catch (e: Exception) {
                        Log.e(TAG, "Couldn't load entities for server", e)
                        emptyList()
                    }
                }
            }.awaitAll()
            loadEntities(serverManager.getServer()?.id ?: 0)
        }
    }

    fun onMove(fromItem: ItemPosition, toItem: ItemPosition) {
        favoritesList.apply {
            add(
                favoritesList.indexOfFirst { it == toItem.key },
                removeAt(favoritesList.indexOfFirst { it == fromItem.key })
            )
        }
    }

    fun canDragOver(position: ItemPosition) = favoritesList.any { it == position.key }

    fun saveFavorites() {
        viewModelScope.launch {
            prefsRepository.setAutoFavorites(favoritesList.toList())
        }
    }

    fun loadEntities(serverId: Int) {
        sortedEntities = entities[serverId] ?: emptyList()
    }

    fun onEntitySelected(checked: Boolean, entityId: String, serverId: Int) {
        if (checked) {
            favoritesList.add("$serverId-$entityId")
        } else {
            favoritesList.remove("$serverId-$entityId")
        }
        viewModelScope.launch { prefsRepository.setAutoFavorites(favoritesList.toList()) }
    }
}
