package io.homeassistant.companion.android.complications

import android.app.Application
import android.util.Log
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.HomeAssistantApplication
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.domain
import io.homeassistant.companion.android.common.data.integration.friendlyName
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.data.websocket.WebSocketState
import io.homeassistant.companion.android.data.SimplifiedEntity
import io.homeassistant.companion.android.database.wear.EntityStateComplications
import io.homeassistant.companion.android.database.wear.EntityStateComplicationsDao
import io.homeassistant.companion.android.database.wear.FavoritesDao
import io.homeassistant.companion.android.database.wear.getAllFlow
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

@HiltViewModel
class ComplicationConfigViewModel @Inject constructor(
    application: Application,
    favoritesDao: FavoritesDao,
    private val serverManager: ServerManager,
    private val entityStateComplicationsDao: EntityStateComplicationsDao
) : AndroidViewModel(application) {
    companion object {
        const val TAG = "ComplicationConfigViewModel"
    }

    enum class LoadingState {
        LOADING,
        READY,
        ERROR
    }

    val app = getApplication<HomeAssistantApplication>()

    var entities = mutableStateMapOf<String, Entity<*>>()
        private set
    var entitiesByDomain = mutableStateMapOf<String, SnapshotStateList<Entity<*>>>()
        private set
    var entitiesByDomainOrder = mutableStateListOf<String>()
        private set
    val favoriteEntityIds = favoritesDao.getAllFlow().collectAsState()

    var loadingState by mutableStateOf(LoadingState.LOADING)
        private set
    var selectedEntity: SimplifiedEntity? by mutableStateOf(null)
        private set
    var entityShowTitle by mutableStateOf(true)
        private set
    var entityShowUnit by mutableStateOf(true)
        private set
    var entityForwardTaps by mutableStateOf(true)
        private set
    init {
        loadEntities()
    }

    fun setDataFromIntent(id: Int) {
        viewModelScope.launch {
            if (!serverManager.isRegistered() || id <= 0) return@launch

            val stored = entityStateComplicationsDao.get(id)
            stored?.let {
                selectedEntity = SimplifiedEntity(entityId = it.entityId)
                entityShowTitle = it.showTitle
                entityShowUnit = it.showUnit
                entityForwardTaps = it.forwardTaps
                if (loadingState == LoadingState.READY) {
                    updateSelectedEntity()
                }
            }
        }
    }

    private fun loadEntities() {
        viewModelScope.launch {
            if (!serverManager.isRegistered()) {
                loadingState = LoadingState.ERROR
                return@launch
            }
            try {
                // Load initial state
                loadingState = LoadingState.LOADING
                serverManager.integrationRepository().getEntities()?.forEach {
                    entities[it.entityId] = it
                }
                updateEntityDomains()
                updateSelectedEntity()

                // Finished initial load, update state
                val webSocketState = serverManager.webSocketRepository().getConnectionState()
                if (webSocketState == WebSocketState.CLOSED_AUTH) {
                    loadingState = LoadingState.ERROR
                    return@launch
                }
                loadingState = if (webSocketState == WebSocketState.ACTIVE) {
                    LoadingState.READY
                } else {
                    LoadingState.ERROR
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception while loading entities", e)
                loadingState = LoadingState.ERROR
            }
        }
    }

    private fun updateEntityDomains() {
        val entitiesList = entities.values.toList().sortedBy { it.entityId }
        val domainsList = entitiesList.map { it.domain }.distinct()

        // Create a list with all discovered domains + their entities
        domainsList.forEach { domain ->
            val entitiesInDomain = mutableStateListOf<Entity<*>>()
            entitiesInDomain.addAll(entitiesList.filter { it.domain == domain })
            entitiesByDomain[domain]?.let {
                it.clear()
                it.addAll(entitiesInDomain)
            } ?: run {
                entitiesByDomain[domain] = entitiesInDomain
            }
        }
        entitiesByDomainOrder.clear()
        entitiesByDomainOrder.addAll(domainsList)
    }

    private fun updateSelectedEntity() {
        if (selectedEntity == null) return
        val fullEntity = entities[selectedEntity!!.entityId]

        selectedEntity = if (fullEntity == null) {
            null // Clear invalid value
        } else {
            SimplifiedEntity(
                entityId = fullEntity.entityId,
                friendlyName = fullEntity.friendlyName,
                icon = (fullEntity.attributes as? Map<*, *>)?.get("icon") as? String ?: ""
            )
        }
    }

    fun setEntity(entity: SimplifiedEntity) {
        selectedEntity = entity
    }

    fun setShowTitle(show: Boolean) {
        entityShowTitle = show
    }

    fun setShowUnit(show: Boolean) {
        entityShowUnit = show
    }

    fun setForwardTaps(show: Boolean) {
        entityForwardTaps = show
    }

    fun addEntityStateComplication(id: Int, entity: SimplifiedEntity) {
        viewModelScope.launch {
            entityStateComplicationsDao.add(EntityStateComplications(id, entity.entityId, entityShowTitle, entityShowUnit, entityForwardTaps))
        }
    }

    /**
     * Convert a Flow into a State object that updates until the view model is cleared.
     */
    private fun <T> Flow<T>.collectAsState(
        initial: T
    ): State<T> {
        val state = mutableStateOf(initial)
        viewModelScope.launch {
            collect { state.value = it }
        }
        return state
    }
    private fun <T> Flow<List<T>>.collectAsState(): State<List<T>> = collectAsState(initial = emptyList())
}
