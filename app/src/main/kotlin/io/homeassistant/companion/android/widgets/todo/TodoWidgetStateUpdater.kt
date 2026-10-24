package io.homeassistant.companion.android.widgets.todo

import io.homeassistant.companion.android.common.data.integration.display.EntitiesForDisplayManager
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayState
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.data.websocket.WebSocketRepository
import io.homeassistant.companion.android.database.widget.TodoWidgetDao
import io.homeassistant.companion.android.database.widget.TodoWidgetEntity
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onCompletion
import timber.log.Timber

/**
 * This class allow the [TodoGlanceAppWidget] to get their states while in composition state
 * by exposing a flow through [stateFlow].
 */
internal class TodoWidgetStateUpdater @Inject constructor(
    private val todoWidgetDao: TodoWidgetDao,
    private val serverManager: ServerManager,
    private val entitiesForDisplayManager: EntitiesForDisplayManager,
) {
    /**
     * Observes and provides the state of the widget identified by the given [widgetId].
     *
     * ### Flow details:
     * 1. **Initial state flow**: Emits the current state of the widget using the data in the database. If no configuration exists, it emits an empty state.
     * 2. **Watch for changes flow**: Listens for changes in the widget's configuration or updates from the server. When a change is detected:
     *    - It fetches the latest entity and todo items from the server.
     *    - Updates the database with the latest data.
     *    - Emits the updated state.
     *
     * ### Error handling:
     * - If an error occurs while watching for changes, it logs the error.
     * - The flow completes gracefully when no longer needed, logging a message to indicate the end of observation.
     *
     * @param widgetId The unique identifier of the widget whose state is being observed.
     * @return A [Flow] emitting [TodoState] objects representing the current state of the widget.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun stateFlow(widgetId: Int): Flow<TodoState> {
        val watchForChangeFlow = getTodoEntityOnConfigurationChange(widgetId)
            .flatMapLatest { todoEntity ->
                Timber.d("Got a new entity to watch $todoEntity")
                val serverId = todoEntity.serverId
                val listEntityId = todoEntity.entityId

                // Every emission is a change of the entity or of its name, so each one refreshes
                entitiesForDisplayManager.observe(serverId, listOf(listEntityId))
                    .map { state ->
                        val displayEntity = when (state) {
                            // Nothing was ever loaded for this widget, so there is nothing to show yet
                            EntityDisplayState.Loading if todoEntity.latestUpdateData == null -> {
                                return@map LoadingTodoState
                            }
                            // Still loading, or the entity is gone (removed server, deleted list),
                            // so the widget keeps showing the data of the database
                            EntityDisplayState.Loading, EntityDisplayState.Error -> {
                                return@map TodoStateWithData.from(todoEntity)
                            }
                            is EntityDisplayState.Loaded -> state.entity(listEntityId)
                                ?: return@map TodoStateWithData.from(todoEntity)
                        }
                        Timber.d("Got an update of the entity ${displayEntity.entityId} getting todos")
                        val todos = serverManager.webSocketRepository(serverId).getTodoItems(listEntityId)
                        // We update the DAO to keep it up to date for the next update of the widget
                        todoWidgetDao.updateWidgetLastUpdate(
                            widgetId = widgetId,
                            lastUpdateData = TodoWidgetEntity.LastUpdateData(
                                entityName = displayEntity.name,
                                todos = todos,
                            ),
                        )
                        TodoStateWithData.from(todoEntity, displayEntity, todos)
                    }
            }

        // Initial state should emit before watch but if an issue occur make it explicit in the flow
        return merge(getInitialStateFlow(widgetId), watchForChangeFlow).catch {
            // TODO send error to the widget???
            Timber.e(it, "Error while watching for changes for widget $widgetId")
        }.onCompletion {
            Timber.d("Stop watching for changes for widget $widgetId")
        }
    }

    private suspend fun WebSocketRepository.getTodoItems(listEntityId: String): List<TodoWidgetEntity.TodoItem> {
        return getTodos(listEntityId)?.response?.get(listEntityId)?.items.orEmpty().map {
            TodoWidgetEntity.TodoItem(
                uid = it.uid,
                summary = it.summary,
                status = it.status,
            )
        }
    }

    private fun getTodoEntityOnConfigurationChange(widgetId: Int): Flow<TodoWidgetEntity> {
        // The flow starts with a null dao entity until the configuration is done
        // We emit again when the configuration f the widget change
        return todoWidgetDao.getFlow(widgetId).filterNotNull().distinctUntilChanged { old, new ->
            old.isSameConfiguration(new)
        }
    }

    private fun getInitialStateFlow(widgetId: Int): Flow<TodoState> {
        return suspend { todoWidgetDao.get(widgetId) }.asFlow().map {
            if (it == null) {
                EmptyTodoState
            } else {
                TodoStateWithData.from(it)
            }
        }
    }
}
