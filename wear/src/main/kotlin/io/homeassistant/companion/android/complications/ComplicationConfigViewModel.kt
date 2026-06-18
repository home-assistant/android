package io.homeassistant.companion.android.complications

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.friendlyName
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.data.websocket.WebSocketState
import io.homeassistant.companion.android.data.SimplifiedEntity
import io.homeassistant.companion.android.database.wear.EntityStateComplications
import io.homeassistant.companion.android.database.wear.EntityStateComplicationsDao
import io.homeassistant.companion.android.database.wear.FavoritesDao
import io.homeassistant.companion.android.database.wear.getAllFlow
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

@HiltViewModel
class ComplicationConfigViewModel @Inject constructor(
    application: Application,
    favoritesDao: FavoritesDao,
    private val serverManager: ServerManager,
    private val entityStateComplicationsDao: EntityStateComplicationsDao,
) : AndroidViewModel(application) {

    enum class LoadingState {
        LOADING,
        READY,
        ERROR,
    }

    data class ViewState(
        val loadingState: LoadingState = LoadingState.LOADING,
        val selectedEntity: SimplifiedEntity? = null,
        val entityShowTitle: Boolean = true,
        val entityShowUnit: Boolean = true,
        val entitiesByDomainOrder: List<String> = emptyList(),
        val entitiesByDomain: Map<String, List<Entity>> = emptyMap(),
        val favoriteEntityIds: List<String> = emptyList(),
    )

    private val _viewState = MutableStateFlow(ViewState())
    val viewState = _viewState.asStateFlow()

    init {
        loadEntities()
        viewModelScope.launch {
            favoritesDao.getAllFlow().collect { favorites ->
                _viewState.update { it.copy(favoriteEntityIds = favorites) }
            }
        }
    }

    fun setDataFromIntent(id: Int) {
        viewModelScope.launch {
            if (!serverManager.isRegistered() || id <= 0) return@launch

            val stored = entityStateComplicationsDao.get(id)
            stored?.let {
                _viewState.update { state ->
                    state.copy(
                        selectedEntity = resolveEntity(it.entityId) ?: SimplifiedEntity(entityId = it.entityId),
                        entityShowTitle = it.showTitle,
                        entityShowUnit = it.showUnit,
                    )
                }
            }
        }
    }

    private fun loadEntities() {
        viewModelScope.launch {
            if (!serverManager.isRegistered()) {
                _viewState.update { it.copy(loadingState = LoadingState.ERROR) }
                return@launch
            }
            try {
                _viewState.update { it.copy(loadingState = LoadingState.LOADING) }

                val entitiesList = serverManager.integrationRepository().getEntities()
                    ?.sortedBy { it.entityId }
                    .orEmpty()
                val domainsList = entitiesList.map { it.domain }.distinct()
                val byDomain = domainsList.associateWith { domain ->
                    entitiesList.filter { it.domain == domain }
                }

                val webSocketState = serverManager.webSocketRepository().getConnectionState()
                val newLoadingState = if (webSocketState == WebSocketState.Active) {
                    LoadingState.READY
                } else {
                    LoadingState.ERROR
                }

                _viewState.update { state ->
                    state.copy(
                        entitiesByDomainOrder = domainsList,
                        entitiesByDomain = byDomain,
                        selectedEntity = state.selectedEntity?.let { selected ->
                            resolveEntity(selected.entityId, entitiesList)
                        },
                        loadingState = newLoadingState,
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Exception while loading entities")
                _viewState.update { it.copy(loadingState = LoadingState.ERROR) }
            }
        }
    }

    /**
     * Resolves a [SimplifiedEntity] from the given entity list, or from the current view state
     * if no list is provided. Returns null if the entity is not found.
     */
    private fun resolveEntity(
        entityId: String,
        entitiesList: List<Entity> = _viewState.value.entitiesByDomain.values.flatten(),
    ): SimplifiedEntity? {
        val fullEntity = entitiesList.firstOrNull { it.entityId == entityId } ?: return null
        return SimplifiedEntity(
            entityId = fullEntity.entityId,
            friendlyName = fullEntity.friendlyName,
            icon = fullEntity.attributes["icon"] as? String ?: "",
        )
    }

    fun setEntity(entity: SimplifiedEntity) {
        _viewState.update { it.copy(selectedEntity = entity) }
    }

    fun setShowTitle(show: Boolean) {
        _viewState.update { it.copy(entityShowTitle = show) }
    }

    fun setShowUnit(show: Boolean) {
        _viewState.update { it.copy(entityShowUnit = show) }
    }

    fun addEntityStateComplication(id: Int, entity: SimplifiedEntity) {
        viewModelScope.launch {
            entityStateComplicationsDao.add(
                EntityStateComplications(
                    id = id,
                    entityId = entity.entityId,
                    showTitle = _viewState.value.entityShowTitle,
                    showUnit = _viewState.value.entityShowUnit,
                ),
            )
        }
    }
}
