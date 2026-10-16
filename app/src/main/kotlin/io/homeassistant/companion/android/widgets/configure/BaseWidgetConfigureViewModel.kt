package io.homeassistant.companion.android.widgets.configure

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.RemoteException
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.composable.HADropdownItem
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.util.SdkVersion
import io.homeassistant.companion.android.database.widget.WidgetBackgroundType
import io.homeassistant.companion.android.database.widget.WidgetDao
import io.homeassistant.companion.android.database.widget.WidgetEntity
import io.homeassistant.companion.android.widgets.ACTION_APPWIDGET_CREATED
import io.homeassistant.companion.android.widgets.BaseWidgetProvider
import io.homeassistant.companion.android.widgets.EXTRA_WIDGET_ENTITY
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Shared behaviour of every widget configuration ViewModel: exposing the [state] and [errors],
 * observing the available servers, restoring an existing widget, switching servers, and saving,
 * creating or updating the widget.
 */
internal abstract class BaseWidgetConfigureViewModel<S : WidgetConfigureState<S>, E : WidgetEntity<E>>(
    protected val serverManager: ServerManager,
    protected val widgetId: Int,
    protected val dao: WidgetDao<E>,
    initialState: S,
) : ViewModel(),
    WidgetConfigureStateProvider<S> {

    private val _state = MutableStateFlow(initialState)
    final override val state: StateFlow<S> = _state.asStateFlow()

    private val _errors = Channel<Int>(Channel.BUFFERED)

    /** Errors to surface to the user, as string resources. */
    final override val errors = _errors.receiveAsFlow()

    /** The widget provider, used to build the pin request and the update broadcast. */
    protected abstract val widgetProviderClass: Class<*>

    init {
        viewModelScope.launch { restoreConfiguration() }

        viewModelScope.launch {
            serverManager.serversFlow.collect { servers ->
                val items = servers.map { HADropdownItem(key = it.id, label = it.friendlyName) }
                updateState { it.withServersDropdownItems(items) }
            }
        }
    }

    fun onServerSelected(serverId: Int) {
        if (serverId == currentState().selectedServerId) return

        updateState { it.changeServer(serverId) }
        reload()
    }

    fun onBackgroundTypeSelected(backgroundType: WidgetBackgroundType) {
        updateState { it.withBackgroundType(backgroundType) }
    }

    /**
     * Persists the current configuration, reporting through [errors] and returning false when it
     * cannot be saved.
     */
    suspend fun updateWidgetConfiguration(context: Context): Boolean {
        val widget = if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            Timber.e("Cannot save the widget configuration, the widget ID is invalid")
            null
        } else {
            buildPendingEntity(context)
        }

        return if (widget == null) {
            sendError(commonR.string.widget_update_error)
            false
        } else {
            dao.add(widget)
            true
        }
    }

    /** Asks the already placed widgets to redraw with the configuration that was just saved. */
    // `open` only so Glance widgets can override it; make final once every widget uses Glance.
    open fun updateWidget(context: Context) {
        context.sendBroadcast(
            Intent(context, widgetProviderClass).apply {
                action = BaseWidgetProvider.UPDATE_WIDGETS
            },
        )
    }

    /**
     * Asks the launcher to pin the configured widget and suspends until it is added, reporting
     * through [errors] and returning false when the widget cannot be requested at all.
     */
    suspend fun requestWidgetCreation(context: Context): Boolean {
        // A single when keeps this under detekt's ReturnCount limit instead of a guard clause each.
        val widget = when {
            !SdkVersion.isAtLeast(Build.VERSION_CODES.O) -> {
                Timber.e("Cannot pin the widget, pinning requires API ${Build.VERSION_CODES.O}")
                null
            }
            !isPinningSupported(context) -> {
                Timber.e("Cannot pin the widget, the launcher does not support it")
                null
            }
            else -> buildPendingEntity(context)
        }
        if (widget == null) {
            sendError(commonR.string.widget_creation_error)
            return false
        }

        var requestAccepted = false
        dao.getWidgetCountFlow()
            // We drop the first value since we only care about knowing when the widget is actually added
            .drop(1)
            .onStart {
                requestAccepted = requestPin(context, widget)
                // A rejected request never adds a widget, so emit to stop waiting for one
                if (!requestAccepted) emit(0)
            }.first()

        if (!requestAccepted) {
            Timber.e("The launcher rejected the widget pin request")
            sendError(commonR.string.widget_creation_error)
        }
        return requestAccepted
    }

    /** The current configuration state. */
    protected fun currentState(): S = _state.value

    /** Applies [transform] to the current state. */
    protected fun updateState(transform: (S) -> S) = _state.update(transform)

    /** Surfaces [resId] to the user through [errors]. */
    protected suspend fun sendError(@StringRes resId: Int) {
        _errors.send(resId)
    }

    /**
     * Asks the launcher to pin [widget]. The default requests a classic app-widget pin; Glance-based
     * widgets override this (and [updateWidget]) to use the Glance APIs instead.
     *
     * Only called after [requestWidgetCreation] has checked the API level and launcher support.
     */
    @SuppressLint("NewApi") // Guarded by requestWidgetCreation before this is reached.
    protected open suspend fun requestPin(context: Context, widget: E): Boolean {
        return AppWidgetManager.getInstance(context).requestPinAppWidget(
            ComponentName(context, widgetProviderClass),
            null,
            PendingIntent.getBroadcast(
                context,
                System.currentTimeMillis().toInt(),
                Intent(context, widgetProviderClass).apply {
                    action = ACTION_APPWIDGET_CREATED
                    putExtra(EXTRA_WIDGET_ENTITY, widget)
                },
                // The PendingIntent must be mutable so the system injects the EXTRA_APPWIDGET_ID of the created widget
                PendingIntent.FLAG_MUTABLE,
            ),
        )
    }

    /** Applies the stored configuration of an existing [widget] to the state. */
    protected abstract fun restoreState(state: S, widget: E): S

    /**
     * Builds the DAO entity to persist from the current configuration, or null when it is incomplete
     * or its data cannot be retrieved.
     */
    protected abstract suspend fun buildPendingEntity(context: Context): E?

    /** Refreshes what the current server offers (entities or a template preview). */
    protected abstract fun reload()

    /**
     * Restores the configuration of an existing widget, or falls back to the active server for a new one.
     */
    private suspend fun restoreConfiguration() {
        // A preselected ("add a widget for this entity") launch never carries a widget id, so a valid
        // id is enough to tell an existing widget apart from a new one.
        val widget = if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            dao.get(widgetId)
        } else {
            null
        }

        if (widget != null) {
            updateState { restoreState(it, widget) }
        } else {
            val activeServerId = serverManager.getServer()?.id ?: ServerManager.SERVER_ID_ACTIVE
            updateState { it.withSelectedServerId(activeServerId) }
        }

        reload()
    }
}

@SuppressLint("NewApi") // Callers guard on the API level before reaching this.
private fun isPinningSupported(context: Context): Boolean = try {
    AppWidgetManager.getInstance(context).isRequestPinAppWidgetSupported
} catch (e: RemoteException) {
    Timber.e(e, "Unable to read isRequestPinAppWidgetSupported")
    false
}
