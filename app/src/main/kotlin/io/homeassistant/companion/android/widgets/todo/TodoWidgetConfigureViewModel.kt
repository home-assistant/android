package io.homeassistant.companion.android.widgets.todo

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.common.data.integration.IntegrationDomains.TODO_DOMAIN
import io.homeassistant.companion.android.common.data.integration.display.EntitiesForDisplayManager
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayState
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayWithContext
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.database.widget.TodoWidgetDao
import io.homeassistant.companion.android.database.widget.TodoWidgetEntity
import io.homeassistant.companion.android.database.widget.WidgetBackgroundType
import io.homeassistant.companion.android.widgets.ACTION_APPWIDGET_CREATED
import io.homeassistant.companion.android.widgets.EXTRA_WIDGET_ENTITY
import io.homeassistant.companion.android.widgets.configure.BaseEntityWidgetConfigureViewModel
import io.homeassistant.companion.android.widgets.configure.WidgetTextColor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import timber.log.Timber

@HiltViewModel(assistedFactory = TodoWidgetConfigureViewModel.Factory::class)
internal class TodoWidgetConfigureViewModel @AssistedInject constructor(
    todoWidgetDao: TodoWidgetDao,
    serverManager: ServerManager,
    entitiesForDisplayManager: EntitiesForDisplayManager,
    @Assisted widgetId: Int,
    @Assisted preselectedEntityId: String?,
) : BaseEntityWidgetConfigureViewModel<TodoWidgetConfigureState, TodoWidgetEntity>(
    serverManager = serverManager,
    widgetId = widgetId,
    dao = todoWidgetDao,
    entitiesForDisplayManager = entitiesForDisplayManager,
    initialState = TodoWidgetConfigureState(
        selectedEntityId = preselectedEntityId,
    ),
    entityFilter = { it.domain == TODO_DOMAIN },
) {
    override val widgetProviderClass: Class<*> = TodoWidget::class.java

    fun onEntitySelected(entityId: String?) {
        updateState { it.copy(selectedEntityId = entityId) }
    }

    fun onShowCompletedChanged(showCompleted: Boolean) {
        updateState { it.copy(showCompleted = showCompleted) }
    }

    fun onTextColorSelected(colorHex: String) {
        updateState { it.copy(textColorHex = colorHex) }
    }

    override fun withEntityDisplayState(
        state: TodoWidgetConfigureState,
        displayState: EntityDisplayState<EntityDisplayWithContext>,
    ) = state.copy(entityDisplayState = displayState)

    /** Selects the first list once the entities resolve, so a new widget starts with a selection. */
    override fun onEntitiesLoaded(displayState: EntityDisplayState<EntityDisplayWithContext>) {
        val firstEntityId = (displayState as? EntityDisplayState.Loaded)?.entities?.firstOrNull()?.entityId
        updateState { it.copy(selectedEntityId = it.selectedEntityId ?: firstEntityId) }
    }

    override fun restoreState(state: TodoWidgetConfigureState, widget: TodoWidgetEntity) = state.copy(
        selectedServerId = widget.serverId,
        selectedEntityId = widget.entityId,
        showCompleted = widget.showCompleted,
        selectedBackgroundType = widget.backgroundType,
        textColorHex = widget.textColor,
        isUpdateWidget = true,
    )

    override suspend fun buildPendingEntity(context: Context): TodoWidgetEntity? {
        val current = currentState()
        // The selected list must still exist on a known server, which the picker state tells us.
        val entity = current.selectedEntity?.takeIf { serverManager.getServer(current.selectedServerId) != null }
        if (entity == null) {
            Timber.e("Cannot build the widget, the current configuration is invalid")
        }

        val todos = entity?.let { serverManager.loadTodos(current.selectedServerId, it.entityId) }

        return if (entity != null && todos != null) {
            TodoWidgetEntity(
                id = widgetId,
                serverId = current.selectedServerId,
                entityId = entity.entityId,
                backgroundType = current.selectedBackgroundType,
                textColor = if (current.selectedBackgroundType == WidgetBackgroundType.TRANSPARENT) {
                    WidgetTextColor.fromHex(context, current.textColorHex).resolve(context)
                } else {
                    null
                },
                showCompleted = current.showCompleted,
                latestUpdateData = TodoWidgetEntity.LastUpdateData(
                    entityName = entity.name,
                    todos = todos,
                ),
            )
        } else {
            null
        }
    }

    /** To-do widgets are Glance widgets, so pin and update go through the Glance APIs. */
    @SuppressLint("NewApi") // Guarded by requestWidgetCreation before this is reached.
    override suspend fun requestPin(context: Context, widget: TodoWidgetEntity): Boolean {
        return GlanceAppWidgetManager(context).requestPinGlanceAppWidget(
            receiver = TodoWidget::class.java,
            successCallback = PendingIntent.getBroadcast(
                context,
                System.currentTimeMillis().toInt(),
                Intent(context, TodoWidget::class.java).apply {
                    action = ACTION_APPWIDGET_CREATED
                    putExtra(EXTRA_WIDGET_ENTITY, widget)
                },
                // The PendingIntent must be mutable so the system injects the EXTRA_APPWIDGET_ID of the created widget
                PendingIntent.FLAG_MUTABLE,
            ),
        )
    }

    override fun updateWidget(context: Context) {
        val appContext = context.applicationContext
        viewModelScope.launch {
            val glanceId = GlanceAppWidgetManager(appContext).getGlanceIdBy(widgetId)
            TodoGlanceAppWidget().update(appContext, glanceId)
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(widgetId: Int, preselectedEntityId: String?): TodoWidgetConfigureViewModel
    }
}

/**
 * Fetches the items of the list so the widget shows content as soon as it is placed, or null
 * when they cannot be retrieved.
 */
private suspend fun ServerManager.loadTodos(serverId: Int, entityId: String): List<TodoWidgetEntity.TodoItem>? = try {
    webSocketRepository(serverId)
        .getTodos(entityId)
        ?.response
        ?.get(entityId)
        ?.items
        .orEmpty()
        .map {
            TodoWidgetEntity.TodoItem(
                uid = it.uid,
                summary = it.summary,
                status = it.status,
            )
        }
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Timber.e(e, "Failed to load the items of the selected list")
    null
}
