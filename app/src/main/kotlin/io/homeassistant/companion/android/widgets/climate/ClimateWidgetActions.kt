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
import io.homeassistant.companion.android.database.widget.ClimateWidgetEntity
import timber.log.Timber

/**
 * Get an Action that will set the given temp of a Climate widget once given to Glance.
 */
internal fun actionIncreaseTemp(): Action {
    return actionRunCallback<ControlClimateAction>(actionParametersOf(IS_INCREASE_KEY to true))
}

/**
 * Get an Action that will set the given temp of a Climate widget once given to Glance.
 */
internal fun actionDecreaseTemp(): Action {
    return actionRunCallback<ControlClimateAction>(actionParametersOf(IS_INCREASE_KEY to false))
}

/**
 * Get an Action that will set the given temp of a Climate widget once given to Glance.
 */
internal fun actionSetHvacMode(hvcaMode: HvacMode): Action {
    return actionRunCallback<ControlClimateAction>(actionParametersOf(HVAC_MODE_KEY to hvcaMode))
}

/**
 * Get an Action that will refresh the Climate widget once given to Glance.
 */
internal fun actionRefreshClimate(): Action {
    return actionRunCallback<RefreshAction>()
}

class NoOpAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        // nada
    }
}

/**
 * Get a dummy Action that does nothing.
 */
fun actionNoOp(): Action = actionRunCallback<NoOpAction>()


/**
 * Basic action that will refresh the given widget. Use [actionRefreshClimate] to get the
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
 * Action that will set the given [Double] through a given parameters with the key [SET_TEMP_KEY]. Use [actionUpdateTemp] to get the
 * Action for Glance.
 *
 * The action call the server to set the new Temperature. On Success the widget is updated and the state will be updated
 * through the [ClimateWidgetStateUpdater]. On Failure it will show a toast with the failure message.
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

        val isIncrease = parameters[IS_INCREASE_KEY]
        val hvacMode = parameters[HVAC_MODE_KEY]

        val widgetEntity = dao.get(appWidgetId)

        if (widgetEntity == null) {
            Timber.w("Aborting set temp action widget entity is null for $appWidgetId")
            return
        }

        if (serverManager.getServer(widgetEntity.serverId) == null) {
            Timber.w("Aborting the server has been removed, widget needs to be configured again")
            return
        }

        if (isIncrease != null) {
            serverManager.setClimateTemp(widgetEntity, isIncrease)
        } else if (hvacMode != null) {
            serverManager.setClimateHvacMode(widgetEntity, hvacMode)
        } else {
            Timber.w("Aborting set temp action because isIncrease and hvacMode are null")
        }

        ClimateGlanceAppWidget().update(context, glanceId)
    }

    private suspend fun ServerManager.setClimateTemp(
        widgetEntity: ClimateWidgetEntity,
        isIncrease: Boolean
    ) {
        val newTemp = widgetEntity.latestUpdateData?.calculateNewTemp(isIncrease)

        if (newTemp == null) {
            Timber.w("Aborting set temp action, newTemp calculation is null")
            return
        }

        val result = webSocketRepository(widgetEntity.serverId).setClimateTemperature(
                entityId = widgetEntity.entityId,
                newTemp = newTemp.toString()
            )


        if (!result) {
            Timber.e("Failed to setClimateTemperature")
            // We cannot update the UI from an action nor send a toast, we don't have any UI context.
            // TODO we could modify the entry in DB to add the error message
        }
    }

    private suspend fun ServerManager.setClimateHvacMode(widgetEntity: ClimateWidgetEntity, hvacMode: HvacMode) {

        val result = webSocketRepository(widgetEntity.serverId).setClimateHvacMode(
            entityId = widgetEntity.entityId,
            hvacMode = hvacMode.key
        )

        if (!result) {
            Timber.e("Failed to setClimateHvacMode")
            // We cannot update the UI from an action nor send a toast, we don't have any UI context.
            // TODO we could modify the entry in DB to add the error message
        }
    }

    private fun ClimateWidgetEntity.LastUpdateData.calculateNewTemp(isIncrease: Boolean): Double? {
        val current = this.climateTemp ?: return null
        val step = this.stepTemp ?: return null
        val newTemp = current + if (isIncrease) step else -step
        return newTemp.coerceIn(minTemp, maxTemp)
    }
}

@VisibleForTesting
internal val SET_TEMP_KEY = ActionParameters.Key<Double>("TEMP_SETTING_KEY")
@VisibleForTesting
internal val IS_INCREASE_KEY = ActionParameters.Key<Boolean>("IS_INCREASY_KEY")
@VisibleForTesting
internal val HVAC_MODE_KEY = ActionParameters.Key<HvacMode>("HVAC_MODE_KEY")
