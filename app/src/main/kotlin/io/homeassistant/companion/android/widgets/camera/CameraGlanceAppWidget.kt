package io.homeassistant.companion.android.widgets.camera

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.size
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.database.widget.CameraWidgetEntity
import io.homeassistant.companion.android.database.widget.WidgetTapAction
import io.homeassistant.companion.android.frontend.navigation.FrontendTarget
import io.homeassistant.companion.android.frontend.navigation.FrontendTarget.Companion.toRawPath
import io.homeassistant.companion.android.util.compose.actionStartFrontend

/** Glance renderer for a configured camera or picture entity. */
class CameraGlanceAppWidget : GlanceAppWidget() {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    internal interface EntryPointContract {
        fun cameraWidgetStateUpdater(): CameraWidgetStateUpdater
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val widgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        provideContent {
            val updater = remember { cameraWidgetStateUpdaterForContext(context) }
            val state by remember(widgetId) { updater.stateFlow(widgetId) }.collectAsState(CameraWidgetState.Loading)
            CameraWidgetContent(state)
        }
    }

    override fun onCompositionError(context: Context, glanceId: GlanceId, appWidgetId: Int, throwable: Throwable) {
        cameraWidgetStateUpdaterForContext(context).compositionError(appWidgetId)
        super.onCompositionError(context, glanceId, appWidgetId, throwable)
    }
}

internal fun cameraWidgetStateUpdaterForContext(
    context: Context,
    entryPointProvider: (Context) -> CameraGlanceAppWidget.EntryPointContract = { applicationContext ->
        EntryPointAccessors.fromApplication(
            applicationContext,
            CameraGlanceAppWidget.EntryPointContract::class.java,
        )
    },
): CameraWidgetStateUpdater = entryPointProvider(context.applicationContext).cameraWidgetStateUpdater()

@Composable
private fun CameraWidgetContent(state: CameraWidgetState) {
    val context = LocalContext.current
    val widget = (state as? CameraWidgetState.Content)?.widget
    var modifier = GlanceModifier.fillMaxSize().appWidgetBackground()
    if (widget != null) modifier = modifier.clickable(widgetTapAction(widget))
    val description = context.getString(commonR.string.widget_camera_contentdescription)

    Box(modifier, contentAlignment = Alignment.Center) {
        val bitmap = (state as? CameraWidgetState.Content)?.bitmap
        if (bitmap == null) {
            Image(
                provider = ImageProvider(android.R.drawable.ic_menu_camera),
                contentDescription = description,
                modifier = GlanceModifier.size(48.dp),
            )
        } else {
            Image(
                provider = ImageProvider(bitmap),
                contentDescription = description,
                contentScale = ContentScale.Fit,
                modifier = GlanceModifier.fillMaxSize().semantics { contentDescription = description },
            )
        }
        if ((state as? CameraWidgetState.Content)?.error == true) {
            Column(
                modifier = GlanceModifier.fillMaxSize(),
                verticalAlignment = Alignment.Bottom,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Image(
                    provider = ImageProvider(R.drawable.ic_sync_problem),
                    contentDescription = context.getString(commonR.string.widget_entity_fetch_error),
                    modifier = GlanceModifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun widgetTapAction(widget: CameraWidgetEntity): Action = when (widget.tapAction) {
    WidgetTapAction.OPEN -> actionStartFrontend(
        FrontendTarget.EntityMoreInfo(widget.entityId).toRawPath().orEmpty(),
        widget.serverId,
    )
    else -> actionRunCallback<CameraWidgetRefreshAction>()
}

/** Glance callback used for the existing refresh tap action. */
class CameraWidgetRefreshAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val updater = cameraWidgetStateUpdaterForContext(context)
        val manager = GlanceAppWidgetManager(context)
        updater.refresh(manager.getAppWidgetId(glanceId))
        CameraGlanceAppWidget().update(context, glanceId)
    }
}
