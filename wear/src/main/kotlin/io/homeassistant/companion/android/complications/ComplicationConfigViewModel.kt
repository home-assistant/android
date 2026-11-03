package io.homeassistant.companion.android.complications

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.common.data.integration.display.EntitiesForDisplayManager
import io.homeassistant.companion.android.common.data.integration.display.EntityBrowser
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplay
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.data.websocket.WebSocketState
import io.homeassistant.companion.android.common.util.mdiName
import io.homeassistant.companion.android.data.SimplifiedEntity
import io.homeassistant.companion.android.database.wear.EntityStateComplications
import io.homeassistant.companion.android.database.wear.EntityStateComplicationsDao
import io.homeassistant.companion.android.database.wear.FavoritesDao
import io.homeassistant.companion.android.database.wear.getAllFlow
import javax.inject.Inject
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

@HiltViewModel
class ComplicationConfigViewModel @Inject constructor(
    application: Application,
    favoritesDao: FavoritesDao,
    private val entitiesForDisplayManager: EntitiesForDisplayManager,
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
        /** Entities of the domains loaded so far, a domain is only loaded once expanded. */
        val entitiesByDomain: Map<String, List<EntityDisplay>> = emptyMap(),
        val loadingDomains: Set<String> = emptySet(),
        val failedDomains: Set<String> = emptySet(),
        val favoriteEntityIds: List<String> = emptyList(),
        val favoriteEntities: List<EntityDisplay> = emptyList(),
    )

    private val _viewState = MutableStateFlow(ViewState())
    val viewState = _viewState.asStateFlow()

    /** Completed with the browser of the server entities, or null when they can't be browsed. */
    private val browser = CompletableDeferred<EntityBrowser?>()

    init {
        loadEntities()
        viewModelScope.launch {
            favoritesDao.getAllFlow().collect { favorites ->
                _viewState.update { it.copy(favoriteEntityIds = favorites) }
                val favoriteEntities = browser.await()?.load(favorites.map { it.split(",")[0] })
                _viewState.update { it.copy(favoriteEntities = favoriteEntities.orEmpty()) }
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
                        selectedEntity = SimplifiedEntity(entityId = it.entityId),
                        entityShowTitle = it.showTitle,
                        entityShowUnit = it.showUnit,
                    )
                }
                val resolved = browser.await()?.load(listOf(it.entityId))?.firstOrNull()?.toSimplifiedEntity()
                if (resolved != null) {
                    _viewState.update { state ->
                        // Keep a selection the user made in the meantime
                        if (state.selectedEntity?.entityId == it.entityId) {
                            state.copy(selectedEntity = resolved)
                        } else {
                            state
                        }
                    }
                }
            }
        }
    }

    /**
     * Loads the domains of the server entities only, the entities of a domain are loaded when it
     * is expanded (see [onDomainExpanded]) so the watch never downloads every state of the server.
     */
    private fun loadEntities() {
        viewModelScope.launch {
            if (!serverManager.isRegistered()) {
                browser.complete(null)
                _viewState.update { it.copy(loadingState = LoadingState.ERROR) }
                return@launch
            }
            try {
                _viewState.update { it.copy(loadingState = LoadingState.LOADING) }

                val entityBrowser = entitiesForDisplayManager.browse(ServerManager.SERVER_ID_ACTIVE)
                browser.complete(entityBrowser)

                val webSocketState = serverManager.webSocketRepository().getConnectionState()
                val newLoadingState = if (entityBrowser != null && webSocketState == WebSocketState.Active) {
                    LoadingState.READY
                } else {
                    LoadingState.ERROR
                }

                _viewState.update { state ->
                    state.copy(
                        entitiesByDomainOrder = entityBrowser?.domains.orEmpty(),
                        loadingState = newLoadingState,
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Exception while loading entities")
                browser.complete(null)
                _viewState.update { it.copy(loadingState = LoadingState.ERROR) }
            }
        }
    }

    /** Loads the entities of [domain] unless they are loaded or loading already. */
    fun onDomainExpanded(domain: String) {
        val state = _viewState.value
        if (domain in state.entitiesByDomain || domain in state.loadingDomains) return

        _viewState.update {
            it.copy(loadingDomains = it.loadingDomains + domain, failedDomains = it.failedDomains - domain)
        }
        viewModelScope.launch {
            val entities = browser.await()?.load(domain)
            _viewState.update {
                if (entities == null) {
                    it.copy(loadingDomains = it.loadingDomains - domain, failedDomains = it.failedDomains + domain)
                } else {
                    it.copy(
                        loadingDomains = it.loadingDomains - domain,
                        entitiesByDomain = it.entitiesByDomain + (domain to entities),
                    )
                }
            }
        }
    }

    private fun EntityDisplay.toSimplifiedEntity() = SimplifiedEntity(
        entityId = entityId,
        name = name,
        icon = statelessIcon.mdiName,
    )

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
