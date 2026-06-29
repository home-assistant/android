package io.homeassistant.companion.android.widgets.climate

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.appwidget.CircularProgressIndicator
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.background
import androidx.glance.appwidget.components.CircleIconButton
import androidx.glance.appwidget.components.Scaffold
import androidx.glance.appwidget.components.SquareIconButton
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.preview.ExperimentalGlancePreviewApi
import androidx.glance.preview.Preview
import androidx.glance.semantics.semantics
import androidx.glance.semantics.testTag
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import dagger.hilt.EntryPoint
import dagger.hilt.EntryPoints
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.database.widget.WidgetBackgroundType
import io.homeassistant.companion.android.util.compose.HomeAssistantGlanceTheme
import io.homeassistant.companion.android.util.compose.HomeAssistantGlanceTypography
import io.homeassistant.companion.android.util.compose.glanceStringResource
import io.homeassistant.companion.android.widgets.climate.ClimateState.Companion.getColors
import timber.log.Timber

// TODO: agregar widget_example_climate.png

/**
 * Glance widget for managing and displaying a Climate component.
 *
 * This widget tries to follow guidelines from https://developer.android.com/design/ui/mobile/guides/widgets/widget_quality_guide
 *
 * This widget display a list from a specified `entity_id` of `climate` domain.
 * It provides functionality to add new items, refresh the list, and toggle the completion status of tasks.
 * The widget's `climate` entity and theme can be set via the [ClimateWidgetConfigureActivity].
 *
 * ### Limitations:
 * - No error messages are displayed except for the out-of-sync indicator.
 * - No loading information is shown for toggle actions.
 * - No information when the widget is not up to date because out of composition.
 *
 */
class ClimateGlanceAppWidget : GlanceAppWidget() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    internal interface ClimateGlanceWidgetEntryPoint {
        fun climateStateUpdater(): ClimateWidgetStateUpdater
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val manager = GlanceAppWidgetManager(context)
        val widgetId = manager.getAppWidgetId(id)

        provideContent {
            val entryPoints = remember { EntryPoints.get(context, ClimateGlanceWidgetEntryPoint::class.java) }
            val flow = remember { entryPoints.climateStateUpdater().stateFlow(widgetId) }

            val state by flow.collectAsState(LoadingClimateState)

            HomeAssistantGlanceTheme(
                colors = state.getColors(),
            ) {
                ScreenForState(state)
            }
        }
    }
}

@Composable
private fun GlanceModifier.climateWidgetBackground(): GlanceModifier {
    return this.appWidgetBackground().fillMaxSize()
        .background(
        GlanceTheme
            .colors.widgetBackground,
    )
}

@Composable
@VisibleForTesting
internal fun ScreenForState(state: ClimateState) {
    when (state) {
        LoadingClimateState -> LoadingScreen()
        EmptyClimateState -> EmptyScreen()
        is ClimateStateWithData -> Screen(state)
    }
}

@Composable
private fun LoadingScreen() {
    Column(
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = GlanceModifier.climateWidgetBackground().fillMaxSize().semantics { testTag = "LoadingScreen" },
    ) {
        CircularProgressIndicator(
            color = GlanceTheme.colors.primary,
            modifier = GlanceModifier.size(HomeAssistantGlanceTheme.dimensions.iconSize),
        )
    }
}

@Composable
private fun EmptyScreen() {
    Column(
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = GlanceModifier.climateWidgetBackground().fillMaxSize().semantics { testTag = "EmptyScreen" },
    ) {
        Image(
            provider = ImageProvider(R.drawable.app_icon_launch),
            contentDescription = null,
            modifier = GlanceModifier.padding(bottom = 8.dp).size(HomeAssistantGlanceTheme.dimensions.iconSize),
        )
        Text(
            text = glanceStringResource(commonR.string.widget_no_configuration),
            style = HomeAssistantGlanceTypography.titleSmall.copy(textAlign = TextAlign.Center),
            modifier = GlanceModifier.padding(horizontal = 8.dp),
        )
    }
}

@Composable
private fun Screen(state: ClimateStateWithData) {
    Scaffold(
        titleBar = {
            TitleBar(outOfSync = state.outOfSync)
        },
        horizontalPadding = 0.dp,
        modifier = GlanceModifier.climateWidgetBackground().semantics { testTag = "Screen" },
    ) {
        if (state.hasDisplayableItems()) {
            ShowClimateContent(
                climateName = state.climateName!!,
                climateTemp = state.climateTemp!!,
                currentTemp = state.currentTemp,
                displayComplete = state.showComplete,
                hvacMode = state.hvacSelectedMode,
                hvacSupportedModes = state.hvacSupportedModes
            )
        } else {
            LoadingScreen()
        }
    }
}

@Composable
private fun ShowClimateContent(
    climateName: String,
    climateTemp: Float?,
    currentTemp: Float?,
    hvacMode: String,
    hvacSupportedModes: List<String>,
    displayComplete: Boolean,
) {
    Column(
        modifier = GlanceModifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SquareIconButton(
                modifier = GlanceModifier.size(HomeAssistantGlanceTheme.dimensions.iconSize)
                    .semantics { testTag = "Add" },
                imageProvider = ImageProvider(androidx.media3.session.R.drawable.media3_icon_minus),    // TODO: ajustar ICON, no viene de donde viene el resto
                contentDescription = LocalContext.current.getString(commonR.string.widget_climate_plus),
                backgroundColor = GlanceTheme.colors.primary,
                onClick = actionDecreaseTemp()

            )

            Text(
                modifier = GlanceModifier.padding(horizontal = 16.dp),
                text = LocalContext.current.getString(commonR.string.widget_climate_format_temp, climateTemp),
                style = HomeAssistantGlanceTypography.titleLarge.copy(
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold
                ),
            )

            SquareIconButton(
                modifier = GlanceModifier.size(HomeAssistantGlanceTheme.dimensions.iconSize)
                    .semantics { testTag = "Add" },
                imageProvider = ImageProvider(R.drawable.ic_plus),
                contentDescription = LocalContext.current.getString(commonR.string.widget_climate_minus),
                backgroundColor = GlanceTheme.colors.primary,
                onClick = actionIncreaseTemp()
            )
        }

        HvacModeSelector(hvacMode, hvacSupportedModes)

        Text(
            modifier = GlanceModifier.padding(16.dp),
            text = climateName,
            style = HomeAssistantGlanceTypography.bodySmall.copy(
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold
            ),
        )
    }
}

@Composable
private fun HvacModeSelector(
    hvacSelectedMode: String,
    supportedModes: List<String>,
) {
    Row(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = GlanceModifier.padding(top = 8.dp),
    ) {
        supportedModes.forEach { mode ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val isEnabled = mode != hvacSelectedMode
                SquareIconButton(
                    modifier = GlanceModifier
                        .size(48.dp)
                        .padding(4.dp),
                    imageProvider = hvacModeIcon(mode),
                    enabled = isEnabled,
                    contentDescription = mode,
                    backgroundColor = if (isEnabled) { GlanceTheme.colors.primary } else GlanceTheme.colors.inversePrimary ,
                    onClick = actionSetHvacMode(mode)
                )

                Text(
                    modifier = GlanceModifier.padding(16.dp),
                    text = mode,
                    style = HomeAssistantGlanceTypography.bodySmall.copy(
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Bold,
                    ),
                )
            }
        }
    }
}

private fun hvacModeIcon(hvacMode: String): ImageProvider {
    val drawable = when (hvacMode) {            // TODO: cargar los estados en el state
        "off" -> R.drawable.hvac_mode_off
        "heat" -> R.drawable.hvac_mode_heat
        "cool" -> R.drawable.hvac_mode_cool
        "dry" -> R.drawable.hvac_mode_dry
        "fan_only" -> R.drawable.hvac_mode_fan
        "auto" -> R.drawable.hvac_mode_auto
        else -> null
    }
    return ImageProvider(drawable ?: R.drawable.ic_bug_report)  // TODO: revisar el failsafe del icon
}

@Composable
private fun TitleBar(outOfSync: Boolean) {
    Row(
        // Try to align the paddings with Google Calendar widget
        modifier = GlanceModifier.padding(top = 12.dp, end = 12.dp, start = 16.dp).fillMaxWidth(),
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        Text(
            text = "Climate Control",    // TODO: mover string hardcode
            style = HomeAssistantGlanceTypography.titleLarge,
            maxLines = 1,
            modifier = GlanceModifier.padding(end = 4.dp).defaultWeight(),
        )
        CircleIconButton(
            modifier = GlanceModifier.size(HomeAssistantGlanceTheme.dimensions.iconSize).semantics {
                testTag = "Refresh"
            },
            contentColor = GlanceTheme.colors.primary,
            imageProvider = if (outOfSync) {
                ImageProvider(
                    R.drawable.ic_sync_problem,
                )
            } else {
                ImageProvider(R.drawable.ic_refresh)
            },
            contentDescription = LocalContext.current.getString(commonR.string.widget_todo_refresh),
            backgroundColor = GlanceTheme.colors.widgetBackground,
            onClick = actionRefreshClimate()
        )
    }
}

@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 360, heightDp = 180)
@Composable
private fun ScreenPreview() {
    HomeAssistantGlanceTheme {
        ScreenForState(
            ClimateStateWithData(
                climateName = "Air name 1",
                backgroundType = WidgetBackgroundType.DYNAMICCOLOR,
                textColor = null,
                serverId = 1,
                listEntityId = "",
                currentTemp = null,
                climateTemp = 12f,
                hvacSelectedMode = "heat",
                hvacSupportedModes = listOf("off", "heat", "cool", "dry"),
                outOfSync = false,
                showComplete = true,
            ),
        )
    }
}

@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 360, heightDp = 180)
@Composable
private fun ScreenPreviewPowerOff() {
    HomeAssistantGlanceTheme {
        ScreenForState(
            ClimateStateWithData(
                climateName = "Air name 1",
                backgroundType = WidgetBackgroundType.DYNAMICCOLOR,
                textColor = null,
                serverId = 1,
                listEntityId = "",
                currentTemp = null,
                climateTemp = 12f,
                hvacSelectedMode = "off",
                hvacSupportedModes = listOf("off", "heat", "cool", "dry"),
                outOfSync = true,
                showComplete = true,
            ),
        )
    }
}

@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 360, heightDp = 180)
@Composable
private fun ScreenPreviewOutOfSync() {
    HomeAssistantGlanceTheme {
        ScreenForState(
            ClimateStateWithData(
                climateName = "Air name 1",
                backgroundType = WidgetBackgroundType.DYNAMICCOLOR,
                textColor = null,
                serverId = 1,
                listEntityId = "",
                currentTemp = null,
                climateTemp = 12f,
                hvacSelectedMode = "heat",
                hvacSupportedModes = listOf("off", "heat", "cool", "dry"),
                outOfSync = true,
                showComplete = true,
            ),
        )
    }
}

@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 360, heightDp = 180)
@Composable
private fun ScreenPreviewEmpty() {
    HomeAssistantGlanceTheme {
        ScreenForState(EmptyClimateState)
    }
}

@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 360, heightDp = 180)
@Composable
private fun ScreenPreviewLoading() {
    HomeAssistantGlanceTheme {
        ScreenForState(LoadingClimateState)
    }
}
