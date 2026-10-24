package io.homeassistant.companion.android.widgets.button

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.CircularProgressIndicator
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.preview.ExperimentalGlancePreviewApi
import androidx.glance.preview.Preview
import androidx.glance.semantics.semantics
import androidx.glance.semantics.testTag
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import dagger.hilt.EntryPoint
import dagger.hilt.EntryPoints
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.timoptr.mdiicons.Mdi
import io.github.timoptr.mdiicons.generated.Flash
import io.github.timoptr.mdiicons.toBitmap
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.util.fromHaName
import io.homeassistant.companion.android.database.widget.WidgetBackgroundType
import io.homeassistant.companion.android.util.compose.HomeAssistantGlanceTheme
import io.homeassistant.companion.android.util.compose.HomeAssistantGlanceTypography
import io.homeassistant.companion.android.widgets.button.ButtonWidget.Companion.CALL_SERVICE
import io.homeassistant.companion.android.widgets.button.ButtonWidget.Companion.CALL_SERVICE_AUTH
import io.homeassistant.companion.android.widgets.button.ButtonWidget.Companion.DEFAULT_MAX_ICON_SIZE
import io.homeassistant.companion.android.widgets.button.ButtonWidget.Companion.IS_LOADING_KEY
import io.homeassistant.companion.android.widgets.button.ButtonWidget.Companion.SENT_SUCCESSFUL_KEY
import io.homeassistant.companion.android.widgets.button.ButtonWidgetState.Companion.getColors

private val authKey = ActionParameters.Key<Boolean>("auth")
private val widgetIdKey = ActionParameters.Key<Int>("widgetId")

class ButtonGlanceAppWidget : GlanceAppWidget() {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    internal interface ButtonGlanceWidgetEntryPoint {
        fun buttonStateUpdater(): ButtonWidgetStateUpdater
    }

    override val sizeMode: SizeMode = SizeMode.Exact
    internal val isActionRunningKey = booleanPreferencesKey(IS_LOADING_KEY)
    internal val isActionSuccessKey = booleanPreferencesKey(ButtonWidget.IS_SUCCESS_KEY)
    internal val wasActionSentSuccessfulKey = booleanPreferencesKey(SENT_SUCCESSFUL_KEY)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val manager = GlanceAppWidgetManager(context)
        val widgetId = manager.getAppWidgetId(id)

        provideContent {
            val isActionRunning = currentState(key = isActionRunningKey) ?: false
            val isActionSuccess = currentState(key = isActionSuccessKey) ?: false
            val wasActionSentSuccessful = currentState(key = wasActionSentSuccessfulKey) ?: true

            val entryPoints = remember { EntryPoints.get(context, ButtonGlanceWidgetEntryPoint::class.java) }
            val updater = remember { entryPoints.buttonStateUpdater() }

            LaunchedEffect(widgetId, isActionRunning, wasActionSentSuccessful, isActionSuccess) {
                updater.updateIsActionRunning(widgetId, isActionRunning)
                updater.updateIsActionError(widgetId, !wasActionSentSuccessful)
                updater.updateIsActionSuccess(widgetId, isActionSuccess)
            }

            val flow = remember(widgetId) { updater.getButtonEntityFlow(widgetId) }
            val state by flow.collectAsState(Loading)

            HomeAssistantGlanceTheme(colors = state?.getColors() ?: HomeAssistantGlanceTheme.colors) {
                ScreenForState(
                    id = id,
                    context = context,
                    state = state,
                    isActionRunning = isActionRunning,
                    isActionSuccess = isActionSuccess,
                    wasActionSentSuccessful = wasActionSentSuccessful,
                )
            }
        }
    }
}

class TapAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val auth = parameters[authKey] == true
        val appWidgetId = parameters[widgetIdKey] ?: return

        updateAppWidgetState(context = context, glanceId = glanceId) {
            it[booleanPreferencesKey(IS_LOADING_KEY)] = true
        }

        ButtonGlanceAppWidget().update(context, glanceId)

        val intent = Intent(context, ButtonWidget::class.java).apply {
            action = if (auth) CALL_SERVICE_AUTH else CALL_SERVICE
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }

        context.sendBroadcast(intent)
    }
}

@Composable
private fun ScreenForState(
    id: GlanceId,
    context: Context,
    state: ButtonWidgetState?,
    isActionRunning: Boolean,
    isActionSuccess: Boolean,
    wasActionSentSuccessful: Boolean,
    modifier: Modifier = Modifier,
) {
    val maxIconSize = DEFAULT_MAX_ICON_SIZE

    when {
        isActionRunning -> ActionRunningScreen()
        !wasActionSentSuccessful -> ActionFailureScreen(state = state as? ButtonStateWithData)
        isActionSuccess -> ActionSuccessScreen(state = state as? ButtonStateWithData)
        state is ButtonStateWithData -> ButtonScreen(
            context = context,
            state = state,
            maxIconSize = maxIconSize,
        )

        else -> DefaultButtonScreen()
    }
}

@Composable
private fun ActionRunningScreen(modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
        modifier = GlanceModifier.fillMaxSize().buttonWidgetBackground().padding(8.dp),
    ) {
        CircularProgressIndicator(
            color = GlanceTheme.colors.onSurface,
            modifier = GlanceModifier.size(24.dp),
        )
    }
}

@Composable
private fun ActionFailureScreen(state: ButtonStateWithData?, modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
        modifier = GlanceModifier.fillMaxSize().buttonWidgetBackground().padding(8.dp),
    ) {
        Image(
            provider = ImageProvider(R.drawable.ic_close),
            contentDescription = null,
            colorFilter = ColorFilter.tint(GlanceTheme.colors.error),
            modifier = GlanceModifier.size(24.dp),
        )

        state?.label?.let {
            Text(
                text = it,
                style = HomeAssistantGlanceTypography.bodySmall,
                modifier = GlanceModifier.fillMaxWidth().padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun ActionSuccessScreen(state: ButtonStateWithData?, modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
        modifier = GlanceModifier.fillMaxSize().buttonWidgetBackground().padding(8.dp),
    ) {
        Image(
            provider = ImageProvider(R.drawable.ic_check),
            contentDescription = null,
            colorFilter = ColorFilter.tint(GlanceTheme.colors.onSurface),
            modifier = GlanceModifier.size(24.dp),
        )

        state?.label?.let {
            Text(
                text = it,
                style = HomeAssistantGlanceTypography.bodySmall,
                modifier = GlanceModifier.fillMaxWidth().padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun DefaultButtonScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val iconBitmap = remember(context) { Mdi.Flash.toBitmap(context, 24, android.graphics.Color.WHITE) }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
        modifier = GlanceModifier.fillMaxSize().buttonWidgetBackground().padding(8.dp),
    ) {
        Image(
            provider = ImageProvider(iconBitmap),
            contentDescription = null,
            colorFilter = ColorFilter.tint(GlanceTheme.colors.onSurface),
            modifier = GlanceModifier.size(24.dp),
        )
    }
}

@Composable
private fun ButtonScreen(
    context: Context,
    state: ButtonStateWithData?,
    maxIconSize: Int,
    modifier: Modifier = Modifier,
) {
    val iconData = state?.icon?.let { Mdi.fromHaName(it) } ?: Mdi.Flash
    val iconBitmap = iconData.toBitmap(context, 48, android.graphics.Color.WHITE)

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
            )
            .padding(8.dp),
    ) {
        Image(
            provider = ImageProvider(iconBitmap),
            contentDescription = null,
            modifier = GlanceModifier.defaultWeight().fillMaxWidth(),
            contentScale = ContentScale.Fit,
            colorFilter = ColorFilter.tint(GlanceTheme.colors.onSurface),
        )

        state?.label?.let {
            Text(
                text = it,
                style = HomeAssistantGlanceTypography.bodySmall.copy(textAlign = TextAlign.Center),
                modifier = GlanceModifier.fillMaxWidth().padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun GlanceModifier.buttonWidgetBackground(): GlanceModifier {
    return this
        .appWidgetBackground()
        .background(GlanceTheme.colors.surface)
        .semantics { testTag = "button_widget_background" }
}

@OptIn(ExperimentalGlancePreviewApi::class)
@Preview
@Composable
private fun ButtonWidgetPreview() {
    HomeAssistantGlanceTheme {
        ButtonScreen(
            context = LocalContext.current,
            state = ButtonStateWithData(
                id = 1,
                icon = "mdi:flash",
                label = "Button Widget",
                backgroundType = WidgetBackgroundType.DAYNIGHT,
                textColor = null,
                requiresAuthentication = false,
            ),
            maxIconSize = 24,
        )
    }
}
