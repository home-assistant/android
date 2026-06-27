package io.homeassistant.companion.android.widgets.climate

import android.content.Context
import androidx.annotation.VisibleForTesting
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
import io.homeassistant.companion.android.database.widget.ClimateWidgetDao
import timber.log.Timber

/**
 * Get an Action that will toggle the given [todoItem] of a Todo widget once given to Glance.
 */
internal fun actionUpdateTemp(newTemp: Double): Action {
    return actionRunCallback<ControlClimateAction>(actionParametersOf(SET_TEMP_KEY to newTemp))
}

/**
 * Get an Action that will refresh the Todo widget once given to Glance.
 */
internal fun actionRefreshClimate(): Action {
    return actionRunCallback<RefreshAction>()
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

        Timber.d("ON ACTION REFRESH CLIMATE")
        ClimateGlanceAppWidget().update(context, glanceId)
    }
}

/**
 * Action that will toggle the given [todoItem] through a given parameters with the key [TOGGLE_KEY]. Use [actionToggleTodo] to get the
 * Action for Glance.
 *
 * The action call the server to toggle the item. On Success the widget is updated and the state will be updated
 * through the [TodoWidgetStateUpdater]. On Failure it will show a toast with the failure message.
 *
 * Note: This needs to be public since it is instantiated by the Glance framework.
 */
class ControlClimateAction : ActionCallback {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ClimateActionEntryPoint {
        fun serverManager(): ServerManager
        fun climateDao(): ClimateWidgetDao
    }

    @VisibleForTesting
    fun getEntryPoints(context: Context): ClimateActionEntryPoint {
        return EntryPoints.get(context.applicationContext, ClimateActionEntryPoint::class.java)
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
        val dao = entryPoints.climateDao()

        val newTemp = parameters[SET_TEMP_KEY]

        if (newTemp == null) {
            Timber.w("Aborting set temp action because newTemp is null")
            return
        }

        val widgetEntity = dao.get(appWidgetId)

        if (widgetEntity == null) {
            Timber.w("Aborting set temp action widget entity is null for $appWidgetId")
            return
        }

        if (serverManager.getServer(widgetEntity.serverId) == null) {
            Timber.w("Aborting the server has been removed, widget needs to be configured again")
            return
        }

        val result = serverManager.webSocketRepository(widgetEntity.serverId).setClimateTempOrHvac(
            entityId = widgetEntity.entityId,
            newTemp = newTemp.toString()
        )

        if (!result) {
            Timber.e("Failed to set new temp $newTemp")
            // We cannot update the UI from an action nor send a toast, we don't have any UI context.
            // TODO we could modify the entry in DB to add the error message
        }

        ClimateGlanceAppWidget().update(context, glanceId)
    }
}

@VisibleForTesting
internal val SET_TEMP_KEY = ActionParameters.Key<Double>("TEMP_SETTING_KEY")
