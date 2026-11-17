package io.homeassistant.companion.android.widgets.configure

import androidx.lifecycle.viewModelScope
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.display.EntitiesForDisplayManager
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayState
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayWithContext
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.database.widget.WidgetDao
import io.homeassistant.companion.android.database.widget.WidgetEntity
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Base for widgets configured by picking entities from the selected server. Loads the entities the
 * server offers (optionally narrowed by [entityFilter]) and exposes them through the state, which is
 * the [reload] performed when the server changes or the configuration is restored.
 */
internal abstract class BaseEntityWidgetConfigureViewModel<S : WidgetConfigureState<S>, E : WidgetEntity<E>>(
    serverManager: ServerManager,
    widgetId: Int,
    dao: WidgetDao<E>,
    private val entitiesForDisplayManager: EntitiesForDisplayManager,
    initialState: S,
    private val entityFilter: (Entity) -> Boolean = { true },
) : BaseWidgetConfigureViewModel<S, E>(serverManager, widgetId, dao, initialState) {

    private var loadEntitiesJob: Job? = null

    override fun reload() {
        loadEntities()
    }

    /** Loads the entities of the selected server, narrowed by [entityFilter]. */
    protected fun loadEntities() {
        loadEntitiesJob?.cancel()
        loadEntitiesJob = viewModelScope.launch {
            if (!serverManager.isRegistered()) {
                Timber.w("No server registered")
                updateState { withEntityDisplayState(it, EntityDisplayState.Loaded(emptyList())) }
                return@launch
            }
            entitiesForDisplayManager.snapshotInContext(currentState().selectedServerId, entityFilter)
                .collect { displayState ->
                    updateState { withEntityDisplayState(it, displayState) }
                    onEntitiesLoaded(displayState)
                }
        }
    }

    protected abstract fun withEntityDisplayState(
        state: S,
        displayState: EntityDisplayState<EntityDisplayWithContext>,
    ): S

    protected open fun onEntitiesLoaded(displayState: EntityDisplayState<EntityDisplayWithContext>) {}
}
