package io.homeassistant.companion.android.widgets.todo

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.RemoteException
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.material.color.DynamicColors
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.composable.HADropdownItem
import io.homeassistant.companion.android.common.data.integration.IntegrationDomains.TODO_DOMAIN
import io.homeassistant.companion.android.common.data.integration.display.EntitiesForDisplayManager
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayState
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.util.SdkVersion
import io.homeassistant.companion.android.database.widget.TodoWidgetDao
import io.homeassistant.companion.android.database.widget.TodoWidgetEntity
import io.homeassistant.companion.android.database.widget.WidgetBackgroundType
import io.homeassistant.companion.android.widgets.ACTION_APPWIDGET_CREATED
import io.homeassistant.companion.android.widgets.EXTRA_WIDGET_ENTITY
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

@HiltViewModel(assistedFactory = TodoWidgetConfigureViewModel.Factory::class)
class TodoWidgetConfigureViewModel @AssistedInject constructor(
    private val todoWidgetDao: TodoWidgetDao,
    private val serverManager: ServerManager,
    private val entitiesForDisplayManager: EntitiesForDisplayManager,
    @Assisted private val widgetId: Int,
    @Assisted preselectedEntityId: String?,
) : ViewModel() {

    private val _state = MutableStateFlow(
        TodoWidgetConfigureState(
            selectedEntityId = preselectedEntityId,
            dynamicColorAvailable = DynamicColors.isDynamicColorAvailable(),
            selectedBackgroundType = if (DynamicColors.isDynamicColorAvailable()) {
                WidgetBackgroundType.DYNAMICCOLOR
            } else {
                WidgetBackgroundType.DAYNIGHT
            },
        ),
    )
    internal val state: StateFlow<TodoWidgetConfigureState> = _state.asStateFlow()

    private val _errors = MutableSharedFlow<Int>(replay = 1)

    /** Errors to surface to the user, as string resources. */
    val errors = _errors.asSharedFlow()

    private var loadEntitiesJob: Job? = null

    init {
        viewModelScope.launch { restoreConfiguration() }

        viewModelScope.launch {
            serverManager.serversFlow.collect { servers ->
                _state.update { current ->
                    current.copy(
                        serversDropdownItems = servers.map { server ->
                            HADropdownItem(key = server.id, label = server.friendlyName)
                        },
                    )
                }
            }
        }
    }

    fun onServerSelected(serverId: Int) {
        if (serverId == _state.value.selectedServerId) return

        _state.update { it.changeServer(serverId) }
        loadEntities(serverId)
    }

    fun onEntitySelected(entityId: String?) {
        _state.update { it.copy(selectedEntityId = entityId) }
    }

    fun onShowCompletedChanged(showCompleted: Boolean) {
        _state.update { it.copy(showCompleted = showCompleted) }
    }

    fun onBackgroundTypeSelected(backgroundType: WidgetBackgroundType) {
        _state.update { it.copy(selectedBackgroundType = backgroundType) }
    }

    fun onTextColorSelected(colorHex: String) {
        _state.update { it.copy(textColorHex = colorHex) }
    }

    /**
     * Persists the current configuration, reporting through [errors] and returning false when it
     * cannot be saved.
     */
    suspend fun updateWidgetConfiguration(): Boolean {
        val widget = if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            Timber.e("Cannot save the widget configuration, the widget ID is invalid")
            null
        } else {
            getPendingDaoEntity()
        }

        if (widget == null) {
            _errors.emit(commonR.string.widget_update_error)
        } else {
            todoWidgetDao.add(widget)
        }
        return widget != null
    }

    /** Asks the already placed widget to redraw with the configuration that was just saved. */
    fun updateWidget(context: Context) {
        val appContext = context.applicationContext
        viewModelScope.launch {
            val glanceId = GlanceAppWidgetManager(appContext).getGlanceIdBy(widgetId)
            TodoGlanceAppWidget().update(appContext, glanceId)
        }
    }

    /**
     * Asks the launcher to pin the configured widget and suspends until it is added, reporting
     * through [errors] and returning false when the widget cannot be requested at all.
     */
    @SuppressLint("NewApi") // The API 26 requirement is checked below before touching the pinning APIs.
    suspend fun requestWidgetCreation(context: Context): Boolean {
        if (!SdkVersion.isAtLeast(Build.VERSION_CODES.O)) {
            Timber.e("Cannot pin the widget, pinning requires API ${Build.VERSION_CODES.O}")
            _errors.emit(commonR.string.widget_creation_error)
            return false
        }

        if (!isPinningSupported(context)) {
            Timber.e("Cannot pin the widget, the launcher does not support it")
            _errors.emit(commonR.string.widget_creation_error)
            return false
        }

        val widget = getPendingDaoEntity()
        if (widget == null) {
            _errors.emit(commonR.string.widget_creation_error)
            return false
        }

        var requestAccepted = false
        todoWidgetDao.getWidgetCountFlow()
            // We drop the first value since we only care about knowing when the widget is actually added
            .drop(1)
            .onStart {
                requestAccepted = GlanceAppWidgetManager(context).requestPinGlanceAppWidget(
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
                // A rejected request never adds a widget, so emit to stop waiting for one
                if (!requestAccepted) emit(0)
            }.first()

        if (!requestAccepted) {
            Timber.e("The launcher rejected the widget pin request")
            _errors.emit(commonR.string.widget_creation_error)
        }
        return requestAccepted
    }

    /**
     * Restores the configuration of an existing widget, or falls back to the active server for a new one.
     */
    private suspend fun restoreConfiguration() {
        val widget = if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID && _state.value.selectedEntityId == null) {
            todoWidgetDao.get(widgetId)
        } else {
            null
        }

        if (widget != null) {
            _state.update {
                it.copy(
                    selectedServerId = widget.serverId,
                    selectedEntityId = widget.entityId,
                    showCompleted = widget.showCompleted,
                    selectedBackgroundType = widget.backgroundType,
                    textColorHex = widget.textColor,
                    isUpdateWidget = true,
                )
            }
        } else {
            _state.update {
                it.copy(selectedServerId = serverManager.getServer()?.id ?: ServerManager.SERVER_ID_ACTIVE)
            }
        }

        loadEntities(_state.value.selectedServerId)
    }

    /** Loads the to-do lists of the server, selecting the first one when there is no selection yet. */
    private fun loadEntities(serverId: Int) {
        loadEntitiesJob?.cancel()
        loadEntitiesJob = viewModelScope.launch {
            if (!serverManager.isRegistered()) {
                Timber.w("No server registered")
                _state.update { it.copy(entityDisplayState = EntityDisplayState.Loaded(emptyList())) }
                return@launch
            }
            entitiesForDisplayManager.snapshotInContext(serverId) { it.domain == TODO_DOMAIN }
                .collect { displayState ->
                    _state.update { current ->
                        val firstEntityId = (displayState as? EntityDisplayState.Loaded)
                            ?.entities
                            ?.firstOrNull()
                            ?.entityId
                        current.copy(
                            entityDisplayState = displayState,
                            selectedEntityId = current.selectedEntityId ?: firstEntityId,
                        )
                    }
                }
        }
    }

    /**
     * Builds the widget to persist from the current configuration, or null when it is incomplete
     * or the items of the selected list cannot be retrieved.
     */
    private suspend fun getPendingDaoEntity(): TodoWidgetEntity? {
        val current = _state.value
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
                textColor = current.textColorHex.takeIf {
                    current.selectedBackgroundType == WidgetBackgroundType.TRANSPARENT
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

    @AssistedFactory
    interface Factory {
        fun create(widgetId: Int, preselectedEntityId: String?): TodoWidgetConfigureViewModel
    }
}

@SuppressLint("NewApi")
private fun isPinningSupported(context: Context): Boolean = try {
    AppWidgetManager.getInstance(context).isRequestPinAppWidgetSupported
} catch (e: RemoteException) {
    Timber.e(e, "Unable to read isRequestPinAppWidgetSupported")
    false
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
