package io.homeassistant.companion.android.widgets.grid

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
import io.homeassistant.companion.android.common.data.integration.onEntityPressedWithoutState
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.database.widget.GridWidgetDao
import timber.log.Timber

/**
 * Get an Action that will toggle the given Entity of a grid widget once given to Glance.
 */
@Composable
internal fun actionPressEntity(entityId: String): Action {
    return actionRunCallback<PressEntityAction>(actionParametersOf(ENTITY_ID_KEY to entityId))
}

/**
 * Get an Action that will refresh the grid widget once given to Glance.
 */
@Composable
internal fun actionRefreshGrid(): Action {
    return actionRunCallback<RefreshAction>()
}

/**
 * Basic action that will refresh the given widget. Use [actionRefreshGrid] to get the
 * Action for Glance.
 *
 * Note: This needs to be public since it is instantiated by the Glance framework.
 *
 * From the doc https://developer.android.com/design/ui/mobile/guides/widgets/widget_quality_guide#tier2-content:
 * > Widget must let users manually refresh content, if there is an expectation the data refreshes more frequently than the UI.
 */
class RefreshAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        GridGlanceAppWidget().update(context, glanceId)
    }
}

@VisibleForTesting
internal val ENTITY_ID_KEY = ActionParameters.Key<String>("ENTITY_ID_KEY")

/**
 * Action that will toggle the given Entity through a given parameters with the key [ENTITY_ID_KEY]. Use [actionPressEntity] to get the
 * Action for Glance.
 *
 * The action call the server to toggle the item. On Success the widget is updated and the state will be updated
 * through the [GridWidgetStateUpdater]. On Failure it will show a toast with the failure message.
 *
 * Note: This needs to be public since it is instantiated by the Glance framework.
 */
class PressEntityAction : ActionCallback {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface PressEntityActionEntryPoint {
        fun serverManager(): ServerManager
        fun gridWidgetDao(): GridWidgetDao
    }

    @VisibleForTesting
    fun getEntryPoints(context: Context): PressEntityActionEntryPoint {
        return EntryPoints.get(context.applicationContext, PressEntityActionEntryPoint::class.java)
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
        val dao = entryPoints.gridWidgetDao()

        val entityId = parameters[ENTITY_ID_KEY]

        if (entityId == null) {
            Timber.w("Aborting toggle action because entityId is null")
            return
        }

        val widgetEntity = dao.get(appWidgetId)

        if (widgetEntity == null) {
            Timber.w("Aborting press action widget entity is null for $appWidgetId")
            return
        }

        onEntityPressedWithoutState(
            entityId = entityId,
            integrationRepository = serverManager.integrationRepository(widgetEntity.serverId),
        )

        GridGlanceAppWidget().update(context, glanceId)
    }
}
