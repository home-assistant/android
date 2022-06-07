package io.homeassistant.companion.android.complications

import android.app.Application
import android.util.Log
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
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.common.data.integration.domain
import io.homeassistant.companion.android.common.data.websocket.WebSocketRepository
import io.homeassistant.companion.android.common.data.websocket.WebSocketState
import io.homeassistant.companion.android.data.SimplifiedEntity
import io.homeassistant.companion.android.database.wear.EntityStateComplications
import io.homeassistant.companion.android.database.wear.EntityStateComplicationsDao
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ComplicationConfigViewModel @Inject constructor(
    application: Application,
    private val integrationUseCase: IntegrationRepository,
    private val webSocketUseCase: WebSocketRepository,
    private val entityStateComplicationsDao: EntityStateComplicationsDao
) : AndroidViewModel(application) {
    companion object {
        const val TAG = "ComplicationConfigViewModel"
    }

    enum class LoadingState {
        LOADING, READY, ERROR
    }

    val app = getApplication<HomeAssistantApplication>()

    var entities = mutableStateMapOf<String, Entity<*>>()
        private set
    var entitiesByDomain = mutableStateMapOf<String, SnapshotStateList<Entity<*>>>()
        private set
    var entitiesByDomainOrder = mutableStateListOf<String>()
        private set

    var loadingState by mutableStateOf(LoadingState.LOADING)
        private set
    var selectedEntity: SimplifiedEntity? by mutableStateOf(null)
        private set

    init {
        loadEntities()
    }

    private fun loadEntities() {
        viewModelScope.launch {
            if (!integrationUseCase.isRegistered()) {
                loadingState = LoadingState.ERROR
                return@launch
            }
            try {
                // Load initial state
                loadingState = LoadingState.LOADING
                integrationUseCase.getEntities()?.forEach {
                    entities[it.entityId] = it
                }
                updateEntityDomains()

                // Finished initial load, update state
                val webSocketState = webSocketUseCase.getConnectionState()
                if (webSocketState == WebSocketState.CLOSED_AUTH) {
                    loadingState = LoadingState.ERROR
                    return@launch
                }
                loadingState = if (webSocketState == WebSocketState.ACTIVE) {
                    LoadingState.READY
                } else {
                    LoadingState.ERROR
                }

                // Listen for updates
                viewModelScope.launch {
                    integrationUseCase.getEntityUpdates()?.collect {
                        entities[it.entityId] = it
                        updateEntityDomains()
                    }
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

    fun setEntity(entity: SimplifiedEntity) {
        selectedEntity = entity
    }

    fun addEntityStateComplication(id: Int, entity: SimplifiedEntity) {
        viewModelScope.launch {
            entityStateComplicationsDao.add(EntityStateComplications(id, entity.entityId))
        }
    }
}
