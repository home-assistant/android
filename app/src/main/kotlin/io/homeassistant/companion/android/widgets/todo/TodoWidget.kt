package io.homeassistant.companion.android.widgets.todo

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import android.widget.Toast
import androidx.annotation.ColorInt
import androidx.annotation.IdRes
import androidx.annotation.StringRes
import androidx.core.graphics.toColorInt
import androidx.core.os.BundleCompat
import androidx.core.widget.RemoteViewsCompat
import androidx.core.widget.RemoteViewsCompat.setTextViewPaintFlags
import com.google.android.material.color.DynamicColors
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.R as RCommon
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.friendlyName
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.data.websocket.impl.entities.GetTodosResponse
import io.homeassistant.companion.android.database.widget.TodoWidgetDao
import io.homeassistant.companion.android.database.widget.TodoWidgetEntity
import io.homeassistant.companion.android.database.widget.WidgetBackgroundType
import io.homeassistant.companion.android.webview.WebViewActivity
import io.homeassistant.companion.android.widgets.BaseWidgetProvider
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class TodoWidget : BaseWidgetProvider() {

    companion object {
        private const val OPEN_TODO_LIST = "io.homeassistant.companion.android.widgets.todo.TodoWidget.OPEN_TODO"
        private const val ADD_TODO_LIST = "io.homeassistant.companion.android.widgets.todo.TodoWidget.ADD_TODO"
        private const val TOGGLE_TODO_ITEM = "io.homeassistant.companion.android.widgets.todo.TodoWidget.TOGGLE_TODO_ITEM"
        internal const val EXTRA_SERVER_ID = "EXTRA_SERVER_ID"
        internal const val EXTRA_ENTITY_ID = "EXTRA_ENTITY_ID"
        internal const val EXTRA_SHOW_COMPLETED = "EXTRA_SHOW_COMPLETED"
        internal const val EXTRA_TOGGLE_SUMMARY = "EXTRA_TOGGLE_SUMMARY"
        internal const val EXTRA_TOGGLE_TARGET_STATUS = "EXTRA_TOGGLE_TARGET_STATUS"
        internal const val EXTRA_BACKGROUND_TYPE = "EXTRA_BACKGROUND_TYPE"
        internal const val EXTRA_TEXT_COLOR = "EXTRA_TEXT_COLOR"
    }

    @Inject
    lateinit var todoWidgetDao: TodoWidgetDao

    override fun getWidgetProvider(context: Context): ComponentName =
        ComponentName(context, TodoWidget::class.java)

    override suspend fun getWidgetRemoteViews(
        context: Context,
        appWidgetId: Int,
        suggestedEntity: Entity<Map<String, Any>>?
    ): RemoteViews {
        val todo = todoWidgetDao.get(appWidgetId)

        val useDynamicColors = todo?.backgroundType == WidgetBackgroundType.DYNAMICCOLOR && DynamicColors.isDynamicColorAvailable()
        val views = RemoteViews(context.packageName, if (useDynamicColors) R.layout.widget_todo_wrapper_dynamiccolor else R.layout.widget_todo_wrapper_default).apply {
            // can be called before configure screen - no data to show
            if (todo != null) {
                setup(context = context, appWidgetId = appWidgetId, todoEntity = todo)
            }
        }

        return views
    }

    private suspend fun RemoteViews.setup(context: Context, appWidgetId: Int, todoEntity: TodoWidgetEntity) {
        val integrationRepository = serverManager.integrationRepository(todoEntity.serverId)
        val webSocketRepository = serverManager.webSocketRepository(todoEntity.serverId)
        val name = integrationRepository.getEntity(todoEntity.entityId)?.friendlyName
        val todos = webSocketRepository.getTodos(todoEntity.entityId)?.response?.get(todoEntity.entityId)?.items.orEmpty()
            .groupBy { it.isDone }

        setViewVisibility(R.id.widget_todo_preview_items, View.GONE)
        setViewVisibility(R.id.widgetProgressBar, View.INVISIBLE)
        setViewVisibility(R.id.widget_overlay, View.INVISIBLE)

        val textColor = if (todoEntity.backgroundType == WidgetBackgroundType.TRANSPARENT) {
            setInt(R.id.widgetLayout, "setBackgroundColor", Color.TRANSPARENT)
            todoEntity.textColor?.toColorInt()
        } else {
            null
        }
        setTextViewText(R.id.widget_todo_title, name)
        setTextColor(R.id.widget_todo_title, textColor)
        setOnClickPendingIntent(
            R.id.widget_todo_title,
            PendingIntent.getBroadcast(
                context,
                appWidgetId,
                Intent(context, TodoWidget::class.java).apply {
                    action = OPEN_TODO_LIST
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                    putExtra(EXTRA_SERVER_ID, todoEntity.serverId)
                    putExtra(EXTRA_ENTITY_ID, todoEntity.entityId)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )
        setOnClickPendingIntent(
            R.id.widget_todo_refresh,
            PendingIntent.getBroadcast(
                context,
                appWidgetId,
                Intent(context, TodoWidget::class.java).apply {
                    action = UPDATE_VIEW
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )
        setOnClickPendingIntent(
            R.id.widget_todo_add,
            PendingIntent.getBroadcast(
                context,
                appWidgetId,
                Intent(context, TodoWidget::class.java).apply {
                    action = ADD_TODO_LIST
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                    putExtra(EXTRA_SERVER_ID, todoEntity.serverId)
                    putExtra(EXTRA_ENTITY_ID, todoEntity.entityId)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )
        setViewVisibility(R.id.widget_todo_empty_text, if (todos.isEmpty()) View.VISIBLE else View.GONE)
        setViewVisibility(R.id.widget_todo_list, if (todos.isNotEmpty()) View.VISIBLE else View.GONE)

        val remoteCollectionItems = RemoteViewsCompat.RemoteCollectionItems.Builder()
            .setViewTypeCount(2)
            .setHasStableIds(true)
            .apply {
                val activeTodos = todos[false].orEmpty()
                if (activeTodos.isNotEmpty()) {
                    addItem(
                        id = "active".hashCode().toLong(),
                        view = headerRemoteView(
                            context = context,
                            text = RCommon.string.widget_todo_active,
                            textColor = textColor
                        )
                    )
                    activeTodos.forEach { todo ->
                        addItem(
                            id = todo.uid.hashCode().toLong(),
                            view = todo.remoteView(context = context, textColor = textColor)
                        )
                    }
                }

                val doneTodos = todos[true].orEmpty()
                if (doneTodos.isNotEmpty() && todoEntity.showCompleted) {
                    addItem(
                        id = "completed".hashCode().toLong(),
                        view = headerRemoteView(
                            context = context,
                            text = RCommon.string.widget_todo_completed,
                            textColor = textColor
                        )
                    )
                    doneTodos.forEach { todo ->
                        addItem(
                            id = todo.uid.hashCode().toLong(),
                            view = todo.remoteView(context = context, textColor = textColor)
                        )
                    }
                }
            }
            .build()

        val todoIntent = Intent(context, TodoWidget::class.java).apply {
            action = TOGGLE_TODO_ITEM
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            putExtra(EXTRA_SERVER_ID, todoEntity.serverId)
            putExtra(EXTRA_ENTITY_ID, todoEntity.entityId)
        }
        setPendingIntentTemplate(
            R.id.widget_todo_list,
            PendingIntent.getBroadcast(
                context,
                appWidgetId,
                todoIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
        )

        RemoteViewsCompat.setRemoteAdapter(
            context = context,
            remoteViews = this,
            appWidgetId = appWidgetId,
            viewId = R.id.widget_todo_list,
            items = remoteCollectionItems
        )
    }

    private fun GetTodosResponse.Todo.remoteView(context: Context, @ColorInt textColor: Int?) =
        RemoteViews(context.packageName, R.layout.widget_todo_item).apply {
            setTextViewText(R.id.widget_todo_text, summary)
            setTextColor(R.id.widget_todo_text, textColor)
            setTextViewPaintFlags(R.id.widget_todo_text, if (isDone) Paint.STRIKE_THRU_TEXT_FLAG else 0)
            setViewVisibility(R.id.widget_todo_done, if (isDone) View.VISIBLE else View.GONE)
            setViewVisibility(R.id.widget_todo_done_disabled, if (isDone) View.GONE else View.VISIBLE)

            setOnClickFillInIntent(
                R.id.widget_todo_done,
                Intent()
                    .putExtra(EXTRA_TOGGLE_SUMMARY, summary)
                    .putExtra(EXTRA_TOGGLE_TARGET_STATUS, "needs_action")
            )
            setOnClickFillInIntent(
                R.id.widget_todo_done_disabled,
                Intent()
                    .putExtra(EXTRA_TOGGLE_SUMMARY, summary)
                    .putExtra(EXTRA_TOGGLE_TARGET_STATUS, "completed")
            )
        }

    private fun headerRemoteView(context: Context, @StringRes text: Int, @ColorInt textColor: Int?) =
        RemoteViews(context.packageName, R.layout.widget_todo_item_header).apply {
            setTextViewText(R.id.widget_todo_header_text, context.getString(text))
            setTextColor(R.id.widget_todo_header_text, textColor)
        }

    private fun RemoteViews.setTextColor(@IdRes id: Int, @ColorInt color: Int?) {
        if (color != null) {
            setTextColor(id, color)
        }
    }

    private val GetTodosResponse.Todo.isDone: Boolean
        get() = status == "completed"

    override suspend fun getAllWidgetIdsWithEntities(context: Context): Map<Int, Pair<Int, List<String>>> {
        return todoWidgetDao.getAll().associate { widget -> widget.id to (widget.serverId to listOf(widget.entityId)) }
    }

    override fun saveEntityConfiguration(context: Context, extras: Bundle?, appWidgetId: Int) {
        if (extras == null) return
        if (!extras.containsKey(EXTRA_SERVER_ID) || !extras.containsKey(EXTRA_ENTITY_ID)) {
            return
        }

        val serverId = extras.getInt(EXTRA_SERVER_ID)
        val entityId = extras.getString(EXTRA_ENTITY_ID) ?: return
        val backgroundType = BundleCompat.getSerializable(extras, EXTRA_BACKGROUND_TYPE, WidgetBackgroundType::class.java)
            ?: WidgetBackgroundType.DAYNIGHT
        val textColor = extras.getString(EXTRA_TEXT_COLOR)
        val showCompleted = extras.getBoolean(EXTRA_SHOW_COMPLETED)
        widgetScope?.launch {
            todoWidgetDao.add(
                TodoWidgetEntity(
                    id = appWidgetId,
                    serverId = serverId,
                    entityId = entityId,
                    backgroundType = backgroundType,
                    textColor = textColor,
                    showCompleted = showCompleted
                )
            )

            onUpdate(context, AppWidgetManager.getInstance(context), intArrayOf(appWidgetId))
        }
    }

    override suspend fun onEntityStateChanged(
        context: Context,
        appWidgetId: Int,
        entity: Entity<*>
    ) {
        widgetScope?.launch {
            val views = getWidgetRemoteViews(context, appWidgetId, entity as Entity<Map<String, Any>>)
            AppWidgetManager.getInstance(context).updateAppWidget(appWidgetId, views)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (lastIntent) {
            OPEN_TODO_LIST -> context.openTodoList(
                entityId = intent.getStringExtra(EXTRA_ENTITY_ID),
                serverId = intent.getIntExtra(EXTRA_SERVER_ID, ServerManager.SERVER_ID_ACTIVE)
            )
            ADD_TODO_LIST -> context.openTodoList(
                entityId = intent.getStringExtra(EXTRA_ENTITY_ID),
                serverId = intent.getIntExtra(EXTRA_SERVER_ID, ServerManager.SERVER_ID_ACTIVE)
            )
            TOGGLE_TODO_ITEM -> context.toggleItem(
                appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1),
                serverId = intent.getIntExtra(EXTRA_SERVER_ID, ServerManager.SERVER_ID_ACTIVE),
                entityId = intent.getStringExtra(EXTRA_ENTITY_ID) ?: return,
                summary = intent.getStringExtra(EXTRA_TOGGLE_SUMMARY) ?: return,
                targetStatus = intent.getStringExtra(EXTRA_TOGGLE_TARGET_STATUS) ?: return
            )
        }
    }

    private fun Context.toggleItem(appWidgetId: Int, serverId: Int, entityId: String, summary: String, targetStatus: String) {
        val appWidgetManager = AppWidgetManager.getInstance(this)
        val loadingViews = RemoteViews(packageName, R.layout.widget_todo)
        loadingViews.setViewVisibility(R.id.widgetProgressBar, View.VISIBLE)
        loadingViews.setViewVisibility(R.id.widget_overlay, View.VISIBLE)
        appWidgetManager.partiallyUpdateAppWidget(appWidgetId, loadingViews)

        widgetScope?.launch {
            val result = serverManager.webSocketRepository(serverId).updateTodo(
                entityId = entityId,
                todoItem = summary,
                newName = null,
                status = targetStatus
            )

            if (!result) {
                Toast.makeText(this@toggleItem, RCommon.string.action_failure, Toast.LENGTH_LONG).show()

                val views = getWidgetRemoteViews(this@toggleItem, appWidgetId)
                appWidgetManager.updateAppWidget(appWidgetId, views)
            } else {
                sendBroadcast(
                    Intent(this@toggleItem, TodoWidget::class.java).apply {
                        action = UPDATE_VIEW
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                    }
                )
            }
        }
    }

    private fun Context.openTodoList(entityId: String?, serverId: Int) {
        val path = "todo?entity_id=$entityId"
        val intent = Intent(
            WebViewActivity.newInstance(this, path, serverId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        intent.action = path
        intent.addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
        intent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
        startActivity(intent)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        widgetScope?.launch {
            todoWidgetDao.deleteAll(appWidgetIds)
            appWidgetIds.forEach { removeSubscription(it) }
        }
    }
}
