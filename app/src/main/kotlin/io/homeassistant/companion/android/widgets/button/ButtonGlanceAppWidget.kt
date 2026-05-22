package io.homeassistant.companion.android.widgets.button

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.toColorInt
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionSendBroadcast
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProviders
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.material.ColorProviders
import androidx.glance.text.Text
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.IconicsSize
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import com.mikepenz.iconics.utils.padding
import com.mikepenz.iconics.utils.size
import dagger.hilt.EntryPoint
import dagger.hilt.EntryPoints
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.homeassistant.companion.android.database.widget.ButtonWidgetEntity
import io.homeassistant.companion.android.database.widget.WidgetBackgroundType
import io.homeassistant.companion.android.util.compose.HomeAssistantGlanceTheme
import io.homeassistant.companion.android.util.compose.HomeAssistantGlanceTypography
import io.homeassistant.companion.android.util.compose.glanceHaLightColors
import io.homeassistant.companion.android.util.icondialog.getIconByMdiName
import io.homeassistant.companion.android.widgets.button.ButtonWidget.Companion.CALL_SERVICE
import io.homeassistant.companion.android.widgets.button.ButtonWidget.Companion.CALL_SERVICE_AUTH
import io.homeassistant.companion.android.widgets.button.ButtonWidget.Companion.DEFAULT_MAX_ICON_SIZE
import timber.log.Timber

private val authKey = ActionParameters.Key<Boolean>("auth")
private val widgetIdKey = ActionParameters.Key<Int>("widgetId")
class ButtonGlanceAppWidget : GlanceAppWidget() {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    internal interface ButtonGlanceWidgetEntryPoint {
        fun buttonStateUpdater(): ButtonWidgetStateUpdater
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val manager = GlanceAppWidgetManager(context)
        val widgetId = manager.getAppWidgetId(id)

        provideContent {
            val entryPoints = remember { EntryPoints.get(context, ButtonGlanceWidgetEntryPoint::class.java) }
            val flow = remember { entryPoints.buttonStateUpdater().getButtonEntityFlow(widgetId) }

            val state by flow.collectAsState(null)

            Timber.i("GlanceAppWidget $state")
            HomeAssistantGlanceTheme(colors = getWidgetColors(state?.backgroundType, state?.textColor)) {
                ButtonScreen(context, state, DEFAULT_MAX_ICON_SIZE)
            }
        }
    }
}

@Composable
private fun GlanceModifier.buttonWidgetBackground(): GlanceModifier {
    return this.appWidgetBackground().fillMaxSize().background(
        GlanceTheme
            .colors.widgetBackground,
    )
}

@Composable
fun ButtonScreen(context: Context, state: ButtonWidgetEntity?, maxIconSize: Int, modifier: Modifier = Modifier) {
    val iconData = state?.iconName?.let { CommunityMaterial.getIconByMdiName(it) }
        ?: CommunityMaterial.Icon2.cmd_flash // Lightning Bolt
    val iconDrawable = IconicsDrawable(context, iconData).apply {
        padding = IconicsSize.dp(2)
        size = IconicsSize.dp(24)
    }

    // Determine reasonable dimensions for drawing vector icon as a bitmap
    val aspectRatio = iconDrawable.intrinsicWidth / iconDrawable.intrinsicHeight.toDouble()
    val awo = if (state != null) AppWidgetManager.getInstance(context).getAppWidgetOptions(state.id ?: 0) else null
    val maxWidth = (
        awo?.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, maxIconSize)
            ?: maxIconSize
        ).coerceAtLeast(16)
    val maxHeight = (
        awo?.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, maxIconSize)
            ?: maxIconSize
        ).coerceAtLeast(16)
    val width: Int
    val height: Int
    if (maxWidth > maxHeight) {
        width = maxWidth
        height = (maxWidth * (1 / aspectRatio)).toInt()
    } else {
        width = (maxHeight * aspectRatio).toInt()
        height = maxHeight
    }

    val icon = DrawableCompat.wrap(iconDrawable).toBitmap(width, height)

    val requiresAuth = state?.requireAuthentication

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
        modifier = GlanceModifier.fillMaxSize().buttonWidgetBackground()
            .clickable(onClick = actionRunCallback<TapAction>(
                actionParametersOf(
                    authKey to (requiresAuth == true),
                    widgetIdKey to (state?.id ?: -1)
                )
            )),
    ) {
        Image(
            provider = ImageProvider(icon),
            contentDescription = null,
        )
        state?.label?.let {
            Text(text = it, style = HomeAssistantGlanceTypography.bodySmall)
        }
    }
}

@Composable
fun getWidgetColors(
    backgroundType: WidgetBackgroundType?,
    textColor: String?,
    modifier: Modifier = Modifier,
): ColorProviders {
    return when (backgroundType) {
        WidgetBackgroundType.DYNAMICCOLOR -> GlanceTheme.colors
        WidgetBackgroundType.DAYNIGHT -> HomeAssistantGlanceTheme.colors
        WidgetBackgroundType.TRANSPARENT -> ColorProviders(
            glanceHaLightColors
                .copy(
                    background = Color.Transparent,
                    onSurface = Color(
                        textColor?.toColorInt() ?: glanceHaLightColors.onSurface.toArgb(),
                    ),
                ),
        )

        else -> HomeAssistantGlanceTheme.colors
    }
}

class TapAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val auth = parameters[authKey] ?: false
        val widgetId = parameters[widgetIdKey]
        val intent = Intent(context, ButtonWidget::class.java).apply {
            action = if (auth) CALL_SERVICE_AUTH else CALL_SERVICE
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        }
        Timber.i("Action Running, Id: $widgetId, auth: $auth")
        context.sendBroadcast(intent)
        ButtonGlanceAppWidget().update(context, glanceId)
    }
}
