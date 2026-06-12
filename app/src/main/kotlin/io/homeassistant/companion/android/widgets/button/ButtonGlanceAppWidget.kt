package io.homeassistant.companion.android.widgets.button

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.toColorInt
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.CircularProgressIndicator
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.color.ColorProviders
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.size
import androidx.glance.material.ColorProviders
import androidx.glance.semantics.semantics
import androidx.glance.semantics.testTag
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
import io.homeassistant.companion.android.database.widget.WidgetBackgroundType
import io.homeassistant.companion.android.util.compose.HomeAssistantGlanceTheme
import io.homeassistant.companion.android.util.compose.HomeAssistantGlanceTypography
import io.homeassistant.companion.android.util.compose.glanceHaLightColors
import io.homeassistant.companion.android.util.icondialog.getIconByMdiName
import io.homeassistant.companion.android.widgets.button.ButtonWidget.Companion.CALL_SERVICE
import io.homeassistant.companion.android.widgets.button.ButtonWidget.Companion.CALL_SERVICE_AUTH
import io.homeassistant.companion.android.widgets.button.ButtonWidget.Companion.DEFAULT_MAX_ICON_SIZE
import io.homeassistant.companion.android.widgets.button.ButtonWidget.Companion.IS_LOADING_KEY
import kotlinx.coroutines.launch
import timber.log.Timber

private val authKey = ActionParameters.Key<Boolean>("auth")
private val widgetIdKey = ActionParameters.Key<Int>("widgetId")

class ButtonGlanceAppWidget() : GlanceAppWidget() {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    internal interface ButtonGlanceWidgetEntryPoint {
        fun buttonStateUpdater(): ButtonWidgetStateUpdater
    }

    internal val isActionRunningKey = booleanPreferencesKey(IS_LOADING_KEY)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val manager = GlanceAppWidgetManager(context)
        val widgetId = manager.getAppWidgetId(id)

        provideContent {
            val isActionRunning = currentState(key = isActionRunningKey) ?: false

            val entryPoints = remember { EntryPoints.get(context, ButtonGlanceWidgetEntryPoint::class.java) }
            val updater = remember { entryPoints.buttonStateUpdater() }

            LaunchedEffect(widgetId, isActionRunning) {
                Timber.i("Running Launched Effect")
                updater.updateIsActionRunning(widgetId, isActionRunning)
            }

            val flow = remember(widgetId) { updater.getButtonEntityFlow(widgetId) }
            val state by flow.collectAsState(Loading)

            Timber.i("GlanceAppWidget $isActionRunning")
            Timber.i("GlanceAppWidget $state")
            HomeAssistantGlanceTheme(colors = getWidgetColors(state?.backgroundType, state?.textColor)) {
                ScreenForState(
                    context = context,
                    state = state,
                    maxIconSize = DEFAULT_MAX_ICON_SIZE,
                )
            }
        }
    }
}

@Composable
fun ScreenForState(
    context: Context,
    state: ButtonWidgetState?,
    maxIconSize: Int,
    modifier: Modifier = Modifier,
) {
    when (state) {
        Loading -> LoadingScreen()
        is ButtonStateWithData -> ButtonScreen(
            context = context,
            state = state,
            maxIconSize = maxIconSize,
        )

        else -> {}
    }
}

@Composable
fun LoadingScreen(modifier: Modifier = Modifier) {
    Column(
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = GlanceModifier.buttonWidgetBackground().semantics { testTag = "LoadingScreen" },
    ) {
        CircularProgressIndicator(
            color = GlanceTheme.colors.primary,
            modifier = GlanceModifier.size(HomeAssistantGlanceTheme.dimensions.iconSize),
        )
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
private fun ButtonScreen(
    context: Context,
    state: ButtonStateWithData?,
    maxIconSize: Int,
    modifier: Modifier = Modifier,
) {
    val iconData = state?.icon?.let { CommunityMaterial.getIconByMdiName(it) }
        ?: CommunityMaterial.Icon2.cmd_flash // Lightning Bolt
    val iconDrawable = IconicsDrawable(context, iconData).apply {
        padding = IconicsSize.dp(2)
        size = IconicsSize.dp(24)
    }

    val size = LocalSize.current
    // Determine reasonable dimensions for drawing vector icon as a bitmap
    val aspectRatio = iconDrawable.intrinsicWidth / iconDrawable.intrinsicHeight.toDouble()
    val awo = if (state != null) AppWidgetManager.getInstance(context).getAppWidgetOptions(state.id) else null
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

    val icon = DrawableCompat.wrap(iconDrawable).toBitmap(width = width, height = height)

    val requiresAuth = state?.requiresAuthentication

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
        modifier = GlanceModifier.fillMaxSize().buttonWidgetBackground()
            .clickable(
                onClick = actionRunCallback<TapAction>(
                    actionParametersOf(
                        authKey to (requiresAuth == true),
                        widgetIdKey to (state?.id ?: -1),
                    ),
                ),
            ),
    ) {
        Image(
            provider = ImageProvider(icon),
            contentDescription = null,
            modifier = GlanceModifier.height(20.dp),
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
        updateAppWidgetState(context, glanceId) {
            it[booleanPreferencesKey(IS_LOADING_KEY)] = true
        }
        ButtonGlanceAppWidget().update(context, glanceId)
        val auth = parameters[authKey] ?: false
        val widgetId = parameters[widgetIdKey]
        val intent = Intent(context, ButtonWidget::class.java).apply {
            action = if (auth) CALL_SERVICE_AUTH else CALL_SERVICE
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        }
        context.sendBroadcast(intent)
    }
}
