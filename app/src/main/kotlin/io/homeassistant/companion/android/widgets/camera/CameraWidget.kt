package io.homeassistant.companion.android.widgets.camera

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.annotation.VisibleForTesting
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.IntegrationDomains.IMAGE_DOMAIN
import io.homeassistant.companion.android.database.widget.CameraWidgetDao
import io.homeassistant.companion.android.database.widget.CameraWidgetEntity
import io.homeassistant.companion.android.widgets.BaseGlanceEntityWidgetReceiver
import io.homeassistant.companion.android.widgets.BaseWidgetProvider.Companion.UPDATE_WIDGETS
import io.homeassistant.companion.android.widgets.EntitiesPerServer
import javax.inject.Inject
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import timber.log.Timber

private fun newCameraWidgetScope(): CoroutineScope = CoroutineScope(
    Dispatchers.Default + SupervisorJob() + CoroutineExceptionHandler { _, error ->
        Timber.e(error, "Unhandled camera widget receiver work")
    },
)

/** Receiver for the Camera/Picture Glance widget. Its manifest component remains stable for installed widgets. */
@AndroidEntryPoint
class CameraWidget @VisibleForTesting internal constructor(
    widgetScopeProvider: () -> CoroutineScope,
    glanceManagerProvider: (Context) -> GlanceAppWidgetManager,
    private val cameraAppWidgetManagerProvider: (Context) -> AppWidgetManager,
    override val glanceAppWidget: GlanceAppWidget,
) : BaseGlanceEntityWidgetReceiver<CameraWidgetEntity, CameraWidgetDao>(
    widgetScopeProvider,
    glanceManagerProvider,
    cameraAppWidgetManagerProvider,
) {
    constructor() : this(
        ::newCameraWidgetScope,
        { GlanceAppWidgetManager(it) },
        { AppWidgetManager.getInstance(it) },
        CameraGlanceAppWidget(),
    )

    @Inject
    internal lateinit var stateUpdater: CameraWidgetStateUpdater

    override suspend fun getWidgetEntitiesByServer(context: Context): Map<Int, EntitiesPerServer> = dao.getAll()
        .filter { it.entityId.substringBefore('.') == IMAGE_DOMAIN }
        .associate { it.id to EntitiesPerServer(it.serverId, listOf(it.entityId)) }

    override suspend fun onEntityUpdate(context: Context, appWidgetId: Int, entity: Entity) {
        stateUpdater.refreshFromEntity(appWidgetId, entity.state)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        handleCameraWidgetIntent(context, intent)
    }

    internal fun handleCameraWidgetIntent(context: Context, intent: Intent) {
        when (intent.action) {
            UPDATE_IMAGE -> {
                val widgetId = intent.getIntExtra(
                    AppWidgetManager.EXTRA_APPWIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID,
                )
                if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    stateUpdater.refreshAll(intArrayOf(widgetId), force = true)
                    updateGlanceWidget(context, widgetId)
                }
            }
            UPDATE_WIDGETS -> {
                val widgetIds = cameraAppWidgetManagerProvider(context).getAppWidgetIds(componentName(context))
                stateUpdater.refreshAll(widgetIds, force = true)
                updateAllGlanceWidgets(context)
            }
            AppWidgetManager.ACTION_APPWIDGET_UPDATE -> {
                val widgetIds = intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS)
                    ?: cameraAppWidgetManagerProvider(context).getAppWidgetIds(componentName(context))
                stateUpdater.refreshAll(widgetIds, force = false)
            }
            Intent.ACTION_SCREEN_ON -> launchWidgetWork {
                val installedWidgetIds =
                    cameraAppWidgetManagerProvider(context).getAppWidgetIds(componentName(context)).toSet()
                val imageWidgetIds = getWidgetEntitiesByServer(context).keys.filter {
                    it in installedWidgetIds
                }.toIntArray()
                stateUpdater.refreshAll(imageWidgetIds, force = false)
                imageWidgetIds.forEach { updateGlanceWidget(context, it) }
            }
        }
    }

    private fun componentName(context: Context) = ComponentName(context, CameraWidget::class.java)

    companion object {
        internal const val UPDATE_IMAGE =
            "io.homeassistant.companion.android.widgets.camera.CameraWidget.UPDATE_IMAGE"
    }
}
