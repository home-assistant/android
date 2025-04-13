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
import androidx.core.content.ContextCompat
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
import io.homeassistant.companion.android.util.getAttribute
import io.homeassistant.companion.android.webview.WebViewActivity
import io.homeassistant.companion.android.widgets.BaseWidgetProvider
import javax.inject.Inject
import kotlinx.coroutines.launch
import timber.log.Timber

@AndroidEntryPoint
class TodoWidget : BaseWidgetProvider() {

    companion object {
        private const val OPEN_TODO_LIST =
            "io.homeassistant.companion.android.widgets.todo.TodoWidget.OPEN_TODO"
        private const val TOGGLE_TODO_ITEM =
            "io.homeassistant.companion.android.widgets.todo.TodoWidget.TOGGLE_TODO_ITEM"
        internal const val EXTRA_SERVER_ID = "EXTRA_SERVER_ID"
        internal const val EXTRA_ENTITY_ID = "EXTRA_ENTITY_ID"
        internal const val EXTRA_SHOW_COMPLETED = "EXTRA_SHOW_COMPLETED"
        internal const val EXTRA_TOGGLE_SUMMARY = "EXTRA_TOGGLE_SUMMARY"
        internal const val EXTRA_TOGGLE_TARGET_STATUS = "EXTRA_TOGGLE_TARGET_STATUS"
        internal const val EXTRA_BACKGROUND_TYPE = "EXTRA_BACKGROUND_TYPE"
        internal const val EXTRA_TEXT_COLOR = "EXTRA_TEXT_COLOR"

        private data class TodoWidgetData(
            val name: String?,
            val itemsByStatus: Map<Boolean, List<GetTodosResponse.TodoItem>>,
            val error: Boolean = false,
        )
    }

    @Inject
    lateinit var todoWidgetDao: TodoWidgetDao

    override fun getWidgetProvider(context: Context): ComponentName =
        ComponentName(context, TodoWidget::class.java)

    override suspend fun getWidgetRemoteViews(
        context: Context,
        appWidgetId: Int,
        suggestedEntity: Entity<Map<String, Any>>?,
    ): RemoteViews {
        val entity = todoWidgetDao.get(appWidgetId)

        val useDynamicColors =
            entity?.backgroundType == WidgetBackgroundType.DYNAMICCOLOR && DynamicColors.isDynamicColorAvailable()
        val views = RemoteViews(
            context.packageName,
            if (useDynamicColors) R.layout.widget_todo_wrapper_dynamiccolor else R.layout.widget_todo_wrapper_default,
        ).apply {
            if (entity != null) {
                setup(context = context, appWidgetId = appWidgetId, entity = entity)
            } else {
                Timber.d("Todo widget not configured")
            }
        }

        return views
    }

    private suspend fun RemoteViews.setup(
        context: Context,
        appWidgetId: Int,
        entity: TodoWidgetEntity,
    ) {
        val data = readData(entity)

        setInitialVisibility()
        setViewVisibility(R.id.widget_todo_error, if (data.error) View.VISIBLE else View.GONE)
        setClicks(context, entity)

        val textColor = if (entity.backgroundType == WidgetBackgroundType.TRANSPARENT) {
            setInt(R.id.widgetLayout, "setBackgroundColor", Color.TRANSPARENT)
            entity.textColor?.toColorInt()
        } else {
            setInt(R.id.widgetLayout, "setBackgroundResource", R.drawable.widget_button_background)
            context.getAttribute(R.attr.colorWidgetOnBackground, ContextCompat.getColor(context, RCommon.color.colorWidgetButtonLabel))
        }
        setTextViewText(R.id.widget_todo_title, data.name)
        setTextColor(R.id.widget_todo_title, textColor)

        setViewVisibility(
            R.id.widget_todo_empty_text,
            if (data.itemsByStatus.isEmpty()) View.VISIBLE else View.GONE,
        )
        setViewVisibility(
            R.id.widget_todo_list,
            if (data.itemsByStatus.isNotEmpty()) View.VISIBLE else View.GONE,
        )
        val remoteCollectionItems = RemoteViewsCompat.RemoteCollectionItems.Builder()
            .setViewTypeCount(2) // header and item types
            .setHasStableIds(true)
            .setupItems(
                context = context,
                header = RCommon.string.widget_todo_active,
                items = data.itemsByStatus[false].orEmpty(),
                textColor = textColor,
            )
            .setupItems(
                context = context,
                header = RCommon.string.widget_todo_completed,
                items = data.itemsByStatus[true].orEmpty(),
                textColor = textColor,
            )
            .build()

        RemoteViewsCompat.setRemoteAdapter(
            context = context,
            remoteViews = this,
            appWidgetId = appWidgetId,
            viewId = R.id.widget_todo_list,
            items = remoteCollectionItems,
        )
    }

    private fun RemoteViewsCompat.RemoteCollectionItems.Builder.setupItems(
        context: Context,
        @StringRes header: Int,
        items: List<GetTodosResponse.TodoItem>,
        textColor: Int?,
    ): RemoteViewsCompat.RemoteCollectionItems.Builder {
        if (items.isEmpty()) return this
        addItem(
            id = header.toLong(),
            view = headerView(
                context = context,
                text = header,
                textColor = textColor,
            ),
        )
        items.forEach { item ->
            addItem(
                id = item.uid.hashCode().toLong(),
                view = item.todoItemView(context = context, textColor = textColor),
            )
        }

        return this
    }

    private fun RemoteViews.setClicks(context: Context, entity: TodoWidgetEntity) {
        val openIntent = context.createIntent(entity, OPEN_TODO_LIST)
        val openPendingIntent = PendingIntent.getBroadcast(
            context,
            entity.id,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        setOnClickPendingIntent(
            R.id.widget_todo_title,
            openPendingIntent,
        )
        setOnClickPendingIntent(
            R.id.widget_todo_add,
            openPendingIntent,
        )
        setOnClickPendingIntent(
            R.id.widget_todo_refresh,
            PendingIntent.getBroadcast(
                context,
                entity.id,
                context.createIntent(entity, UPDATE_VIEW),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ),
        )

        val toggleIntent = context.createIntent(entity, TOGGLE_TODO_ITEM)
        setPendingIntentTemplate(
            R.id.widget_todo_list,
            PendingIntent.getBroadcast(
                context,
                entity.id,
                toggleIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            ),
        )
    }

    private fun Context.createIntent(
        entity: TodoWidgetEntity,
        action: String,
    ): Intent {
        return Intent(this, TodoWidget::class.java).apply {
            this.action = action
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, entity.id)
            putExtra(EXTRA_SERVER_ID, entity.serverId)
            putExtra(EXTRA_ENTITY_ID, entity.entityId)
        }
    }

    private fun RemoteViews.setInitialVisibility() {
        setViewVisibility(R.id.widget_todo_preview_items, View.GONE)
        setViewVisibility(R.id.widgetProgressBar, View.GONE)
        setViewVisibility(R.id.widget_overlay, View.GONE)
    }

    private suspend fun readData(entity: TodoWidgetEntity): TodoWidgetData {
        val lastEntityName = entity.latestUpdateData?.entityName
        return runCatching {
            val integrationRepository = serverManager.integrationRepository(entity.serverId)
            val webSocketRepository = serverManager.webSocketRepository(entity.serverId)
            val name = integrationRepository.getEntity(entity.entityId)?.friendlyName ?: lastEntityName
            val todos = webSocketRepository.getTodos(entity.entityId)?.response?.get(entity.entityId)?.items.orEmpty()

            updateLatestData(entity.id, name, todos)
            TodoWidgetData(
                name = name,
                itemsByStatus = todos.groupBy { it.isComplete },
            )
        }.getOrElse {
            Timber.e(it, "Failed to read todos")
            val lastTodos = entity.latestUpdateData?.todos?.map { todo ->
                GetTodosResponse.TodoItem(
                    uid = todo.uid,
                    summary = todo.summary,
                    status = todo.status,
                )
            }.orEmpty()
            TodoWidgetData(
                name = lastEntityName,
                itemsByStatus = lastTodos.groupBy { it.isComplete },
                error = true,
            )
        }
    }

    private suspend fun updateLatestData(
        entityId: Int,
        name: String?,
        todos: List<GetTodosResponse.TodoItem>,
    ) = runCatching {
        todoWidgetDao.updateWidgetLastUpdate(
            widgetId = entityId,
            lastUpdateData = TodoWidgetEntity.LastUpdateData(
                entityName = name,
                todos = todos.map {
                    TodoWidgetEntity.TodoItem(
                        uid = it.uid,
                        summary = it.summary,
                        status = it.status,
                    )
                },
            ),
        )
    }.onFailure { Timber.e(it, "Failed to update latest data") }

    private fun GetTodosResponse.TodoItem.todoItemView(context: Context, @ColorInt textColor: Int?) =
        RemoteViews(context.packageName, R.layout.widget_todo_item).apply {
            setTextViewText(R.id.widget_todo_text, summary)
            setTextColor(R.id.widget_todo_text, textColor)
            setTextViewPaintFlags(
                R.id.widget_todo_text,
                if (isComplete) Paint.STRIKE_THRU_TEXT_FLAG else 0,
            )
            setViewVisibility(R.id.widget_todo_done, if (isComplete) View.VISIBLE else View.GONE)
            setViewVisibility(
                R.id.widget_todo_done_disabled,
                if (isComplete) View.GONE else View.VISIBLE,
            )

            setOnClickFillInIntent(
                R.id.widget_todo_done,
                Intent()
                    .putExtra(EXTRA_TOGGLE_SUMMARY, summary)
                    .putExtra(EXTRA_TOGGLE_TARGET_STATUS, GetTodosResponse.TodoItem.NEEDS_ACTION_STATUS),
            )
            setOnClickFillInIntent(
                R.id.widget_todo_done_disabled,
                Intent()
                    .putExtra(EXTRA_TOGGLE_SUMMARY, summary)
                    .putExtra(EXTRA_TOGGLE_TARGET_STATUS, GetTodosResponse.TodoItem.COMPLETED_STATUS),
            )
        }

    private fun headerView(
        context: Context,
        @StringRes text: Int,
        @ColorInt textColor: Int?,
    ) = RemoteViews(context.packageName, R.layout.widget_todo_item_header).apply {
        setTextViewText(R.id.widget_todo_header_text, context.getString(text))
        setTextColor(R.id.widget_todo_header_text, textColor)
    }

    private fun RemoteViews.setTextColor(@IdRes id: Int, @ColorInt color: Int?) {
        if (color != null) {
            setTextColor(id, color)
        }
    }

    private val GetTodosResponse.TodoItem.isComplete: Boolean
        get() = status == GetTodosResponse.TodoItem.COMPLETED_STATUS

    override suspend fun getAllWidgetIdsWithEntities(context: Context): Map<Int, Pair<Int, List<String>>> {
        return todoWidgetDao.getAll()
            .associate { widget -> widget.id to (widget.serverId to listOf(widget.entityId)) }
    }

    override fun saveEntityConfiguration(context: Context, extras: Bundle?, appWidgetId: Int) {
        if (extras == null) return

        val serverId = extras.getInt(EXTRA_SERVER_ID)
        val entityId = extras.getString(EXTRA_ENTITY_ID)
        if (!extras.containsKey(EXTRA_SERVER_ID) || entityId == null) {
            Timber.e("Missing intent extras!")
            return
        }

        val backgroundType = BundleCompat.getSerializable(
            extras,
            EXTRA_BACKGROUND_TYPE,
            WidgetBackgroundType::class.java,
        )
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
                    showCompleted = showCompleted,
                ),
            )

            onUpdate(context, AppWidgetManager.getInstance(context), intArrayOf(appWidgetId))
        }
    }

    override suspend fun onEntityStateChanged(
        context: Context,
        appWidgetId: Int,
        entity: Entity<*>,
    ) {
        widgetScope?.launch {
            val views =
                getWidgetRemoteViews(context, appWidgetId, entity as Entity<Map<String, Any>>)
            AppWidgetManager.getInstance(context).updateAppWidget(appWidgetId, views)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (lastIntent) {
            OPEN_TODO_LIST -> context.openTodoList(
                entityId = intent.getStringExtra(EXTRA_ENTITY_ID),
                serverId = intent.getIntExtra(EXTRA_SERVER_ID, ServerManager.SERVER_ID_ACTIVE),
            )

            TOGGLE_TODO_ITEM -> context.toggleItem(
                appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1),
                serverId = intent.getIntExtra(EXTRA_SERVER_ID, ServerManager.SERVER_ID_ACTIVE),
                entityId = intent.getStringExtra(EXTRA_ENTITY_ID),
                summary = intent.getStringExtra(EXTRA_TOGGLE_SUMMARY),
                targetStatus = intent.getStringExtra(EXTRA_TOGGLE_TARGET_STATUS),
            )
        }
    }

    private fun Context.toggleItem(
        appWidgetId: Int,
        serverId: Int,
        entityId: String?,
        summary: String?,
        targetStatus: String?,
    ) {
        if (entityId == null || summary == null || targetStatus == null) {
            Timber.d("Toggle item missing intent data")
            return
        }
        updateLoadingUI(appWidgetId, View.VISIBLE)

        widgetScope?.launch {
            val result = serverManager.webSocketRepository(serverId).updateTodo(
                entityId = entityId,
                todoItem = summary,
                newName = null,
                status = targetStatus,
            )

            if (!result) {
                Toast.makeText(this@toggleItem, RCommon.string.action_failure, Toast.LENGTH_LONG)
                    .show()
                updateLoadingUI(appWidgetId, View.GONE)
            } else {
                sendBroadcast(
                    Intent(this@toggleItem, TodoWidget::class.java).apply {
                        action = UPDATE_VIEW
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                    },
                )
            }
        }
    }

    private fun Context.updateLoadingUI(appWidgetId: Int, visibility: Int) {
        val appWidgetManager = AppWidgetManager.getInstance(this)
        val loadingViews = RemoteViews(packageName, R.layout.widget_todo)
        loadingViews.setViewVisibility(R.id.widgetProgressBar, visibility)
        loadingViews.setViewVisibility(R.id.widget_overlay, visibility)
        appWidgetManager.partiallyUpdateAppWidget(appWidgetId, loadingViews)
    }

    private fun Context.openTodoList(entityId: String?, serverId: Int) {
        val path = "todo?entity_id=$entityId"
        val intent = Intent(
            WebViewActivity.newInstance(this, path, serverId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
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
