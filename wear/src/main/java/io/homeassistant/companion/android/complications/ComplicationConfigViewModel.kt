package io.homeassistant.companion.android.complications

import android.app.Application
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.HomeAssistantApplication
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.domain
import io.homeassistant.companion.android.common.data.websocket.WebSocketState
import io.homeassistant.companion.android.data.SimplifiedEntity
import io.homeassistant.companion.android.database.AppDatabase
import io.homeassistant.companion.android.database.wear.EntityStateComplications
import io.homeassistant.companion.android.util.supportedDomains
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ComplicationConfigViewModel @Inject constructor(application: Application) : AndroidViewModel(application) {
    companion object {
        const val TAG = "ComplicationConfigViewModel"
    }

    enum class LoadingState {
        LOADING, READY, ERROR
    }

    private lateinit var presenter: ComplicationConfigPresenter
    val app = getApplication<HomeAssistantApplication>()
    private val entityStateComplicationsDao = AppDatabase.getInstance(app.applicationContext).entityStateComplicationsDao()

    // TODO: This is bad, do this instead: https://stackoverflow.com/questions/46283981/android-viewmodel-additional-arguments
    fun init(complicationConfigPresenter: ComplicationConfigPresenter) {
        presenter = complicationConfigPresenter
        loadEntities()
    }

    var entities = mutableStateMapOf<String, Entity<*>>()
        private set
    var entitiesByDomain = mutableStateMapOf<String, SnapshotStateList<Entity<*>>>()
        private set
    var entitiesByDomainOrder = mutableStateListOf<String>()
        private set

    var loadingState = mutableStateOf(LoadingState.LOADING)
        private set
    var selectedEntity = mutableStateOf(SimplifiedEntity("", "", ""))
        private set
    var hasSelected = mutableStateOf(false)
        private set

    private fun loadEntities() {
        viewModelScope.launch {
            if (!presenter.isConnected()) {
                loadingState.value = LoadingState.ERROR
                return@launch
            }
            try {
                // Load initial state
                loadingState.value = LoadingState.LOADING
                presenter.getEntities()?.forEach {
                    if (supportedDomains.contains(it.domain)) {
                        entities[it.entityId] = it
                    }
                }
                updateEntityDomains()

                // Finished initial load, update state
                val webSocketState = presenter.getWebSocketState()
                if (webSocketState == WebSocketState.CLOSED_AUTH) {
                    loadingState.value = LoadingState.ERROR
                    return@launch
                }
                loadingState.value = if (webSocketState == WebSocketState.ACTIVE) {
                    LoadingState.READY
                } else {
                    LoadingState.ERROR
                }

                // Listen for updates
                viewModelScope.launch {
                    presenter.getEntityUpdates()?.collect {
                        if (supportedDomains.contains(it.domain)) {
                            entities[it.entityId] = it
                            updateEntityDomains()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception while loading entities", e)
                loadingState.value = LoadingState.ERROR
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
        selectedEntity.value = entity
        hasSelected.value = true
    }

    fun addEntityStateComplication(id: Int, entity: SimplifiedEntity) {
        viewModelScope.launch {
            entityStateComplicationsDao.add(EntityStateComplications(id, entity.entityId))
        }
    }

    fun removeEntityStateComplication(id: Int) {
        viewModelScope.launch {
            entityStateComplicationsDao.delete(id)
        }
    }
}