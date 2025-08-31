package io.homeassistant.companion.android.widgets.todo

import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.friendlyName
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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import timber.log.Timber

/**
 * This class allow the [TodoGlanceAppWidget] to get their states while in composition state
 * by exposing a flow through [stateFlow].
 */
internal class TodoWidgetStateUpdater @Inject constructor(
    val todoWidgetDao: TodoWidgetDao,
    val serverManager: ServerManager,
) {

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

    private suspend fun getAndSubscribeEntityUpdates(serverId: Int, listEntityId: String): Flow<Entity?>? {
        // Since we might be re-subscribing we might not have get the entity update when subscribing so we query it first
        val currentEntity = serverManager.integrationRepository(serverId).getEntity(listEntityId)

        val entityUpdateFlow = serverManager.integrationRepository(serverId).getEntityUpdates(listOf(listEntityId))

        if (entityUpdateFlow == null) {
            Timber.w("Integration return null for entity update the widget won't update")
        }

        return entityUpdateFlow?.onStart {
            currentEntity?.let {
                emit(currentEntity)
            }
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

                getAndSubscribeEntityUpdates(
                    serverId,
                    listEntityId,
                )?.filterNotNull()?.distinctUntilChanged()?.map { entity ->
                    Timber.d("Got an update of the entity $entity getting todos")
                    val todos = serverManager.webSocketRepository(serverId).getTodoItems(listEntityId)
                    // We update the DAO to keep it up to date for the next update of the widget
                    todoWidgetDao.updateWidgetLastUpdate(
                        widgetId = widgetId,
                        lastUpdateData = TodoWidgetEntity.LastUpdateData(
                            entityName = entity.friendlyName,
                            todos = todos,
                        ),
                    )
                    TodoStateWithData.from(todoEntity, entity, todos)
                } ?: flowOf(TodoStateWithData.from(todoEntity))
            }

        // Initial state should emit before watch but if an issue occur make it explicit in the flow
        return merge(getInitialStateFlow(widgetId), watchForChangeFlow).catch {
            // TODO send error to the widget???
            Timber.e(it, "Error while watching for changes for widget $widgetId")
        }.onCompletion {
            Timber.d("Stop watching for changes for widget $widgetId")
        }
    }
}
