package io.homeassistant.companion.android.widgets.todo

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.compose.runtime.Composable
import androidx.glance.GlanceId
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import dagger.hilt.EntryPoint
import dagger.hilt.EntryPoints
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.data.websocket.impl.entities.GetTodosResponse.TodoItem.Companion.COMPLETED_STATUS
import io.homeassistant.companion.android.common.data.websocket.impl.entities.GetTodosResponse.TodoItem.Companion.NEEDS_ACTION_STATUS
import io.homeassistant.companion.android.database.widget.TodoWidgetDao
import io.homeassistant.companion.android.util.compose.actionStartWebView
import io.homeassistant.companion.android.webview.WebViewActivity
import timber.log.Timber

/**
 * Get an Action that will toggle the given [todoItem] of a Todo widget once given to Glance.
 */
@Composable
internal fun actionToggleTodo(todoItem: TodoItemState): Action {
    return actionRunCallback<ToggleTodoAction>(actionParametersOf(TOGGLE_KEY to todoItem))
}

/**
 * Get an Action that will refresh the Todo widget once given to Glance.
 */
@Composable
internal fun actionRefreshTodo(): Action {
    return actionRunCallback<RefreshAction>()
}

/**
 * Get an Action that will open the [WebViewActivity] for the given [listEntityId]
 */
@Composable
internal fun actionOpenTodolist(listEntityId: String, serverId: Int): Action {
    return actionStartWebView("todo?entity_id=$listEntityId", serverId)
}

/**
 * Basic action that will refresh the given widget. Use [actionRefreshTodo] to get the
 * Action for Glance.
 *
 * Note: This needs to be public since it is instantiated by the Glance framework.
 *
 * From the doc https://developer.android.com/design/ui/mobile/guides/widgets/widget_quality_guide#tier2-content:
 * > Widget must let users manually refresh content, if there is an expectation the data refreshes more frequently than the UI.
 */
class RefreshAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        TodoGlanceAppWidget().update(context, glanceId)
    }
}

@VisibleForTesting
internal val TOGGLE_KEY = ActionParameters.Key<TodoItemState>("TOGGLE_ITEM")

/**
 * Action that will toggle the given [todoItem] through a given parameters with the key [TOGGLE_KEY]. Use [actionToggleTodo] to get the
 * Action for Glance.
 *
 * The action call the server to toggle the item. On Success the widget is updated and the state will be updated
 * through the [TodoWidgetStateUpdater]. On Failure it will show a toast with the failure message.
 *
 * Note: This needs to be public since it is instantiated by the Glance framework.
 */
class ToggleTodoAction : ActionCallback {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ToggleTodoActionEntryPoint {
        fun serverManager(): ServerManager
        fun dao(): TodoWidgetDao
    }

    @VisibleForTesting
    fun toggleStatus(done: Boolean): String {
        return if (done) NEEDS_ACTION_STATUS else COMPLETED_STATUS
    }

    @VisibleForTesting
    fun getEntryPoints(context: Context): ToggleTodoActionEntryPoint {
        return EntryPoints.get(context.applicationContext, ToggleTodoActionEntryPoint::class.java)
    }

    @VisibleForTesting
    fun getGlanceManager(context: Context): GlanceAppWidgetManager {
        return GlanceAppWidgetManager(context)
    }

    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val entryPoints = getEntryPoints(context)
        val serverManager = entryPoints.serverManager()
        val glanceManager = getGlanceManager(context)
        val appWidgetId = glanceManager.getAppWidgetId(glanceId)
        val dao = entryPoints.dao()

        val todoItem = parameters[TOGGLE_KEY]

        if (todoItem == null || todoItem.uid == null) {
            Timber.w("Aborting toggle action because the todo item or uid is null ($todoItem)")
            return
        }

        val widgetEntity = dao.get(appWidgetId)

        if (widgetEntity == null) {
            Timber.w("Aborting toggle action widget entity is null for $appWidgetId")
            return
        }

        val result = serverManager.webSocketRepository(widgetEntity.serverId).updateTodo(
            entityId = widgetEntity.entityId,
            todoItem = todoItem.uid,
            newName = null,
            status = toggleStatus(todoItem.done),
        )

        if (!result) {
            Timber.e("Fail to toggle $todoItem")
            // We cannot update the UI from an action nor send a toast, we don't have any UI context.
            // TODO we could modify the entry in DB to add the error message
        }

        TodoGlanceAppWidget().update(context, glanceId)
    }
}
