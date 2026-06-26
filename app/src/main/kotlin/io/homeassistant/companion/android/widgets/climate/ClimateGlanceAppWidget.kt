package io.homeassistant.companion.android.widgets.climate

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
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
import io.homeassistant.companion.android.widgets.todo.TodoWidgetStateUpdater

// TODO: agregar widget_example_climate.png

/**
 * Glance widget for managing and displaying a Climate component.
 *
 * This widget tries to follow guidelines from https://developer.android.com/design/ui/mobile/guides/widgets/widget_quality_guide
 *
 * This widget display a list from a specified `entity_id` of `todo` domain.
 * It provides functionality to add new items, refresh the list, and toggle the completion status of tasks.
 * The widget's `todo` entity and theme can be set via the [TodoWidgetConfigureActivity].
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
        fun stateUpdater(): TodoWidgetStateUpdater  // TODO crear tipo
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val manager = GlanceAppWidgetManager(context)
        val widgetId = manager.getAppWidgetId(id)

        provideContent {
            val entryPoints = remember { EntryPoints.get(context, ClimateGlanceWidgetEntryPoint::class.java) }
            val flow = remember { entryPoints.stateUpdater().stateFlow(widgetId) }

            val state by flow.collectAsState(LoadingClimateState)

            HomeAssistantGlanceTheme(
                colors = GlanceTheme.colors //state.getColors(), // TODO: crear el stateUpdater para climate
            ) {
//                ScreenForState(state)                     // TODO: crear el stateUpdater para climate
                ScreenForState(LoadingClimateState)
            }
        }
    }
}

@Composable
private fun GlanceModifier.climateWidgetBackground(): GlanceModifier {
    return this.appWidgetBackground().fillMaxSize().background(
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
        modifier = GlanceModifier.climateWidgetBackground().semantics { testTag = "LoadingScreen" },
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
        modifier = GlanceModifier.climateWidgetBackground().semantics { testTag = "EmptyScreen" },
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
            TitleBar(
                climateTemp = state.climateTemp,
                currentTemp = state.currentTemp,
                serverId = state.serverId,
                outOfSync = state.outOfSync,
            )
        },
        // We manually set the padding on each item since the checkbox comes with an embedded padding that
        // we cannot modify.
        horizontalPadding = 0.dp,
        modifier = GlanceModifier.climateWidgetBackground().semantics { testTag = "Screen" },
    ) {
        if (state.isControlEnabled()) {
            ShowClimateContent(
                state.climateTemp,
                state.currentTemp,
                state.showComplete,
                onActionPlus = {},
                onActionMinus = {}
            )
        } else {
            EmptyContent()
        }
    }
}

@Composable
private fun EmptyContent() {
    Text(
        text = glanceStringResource(commonR.string.widget_todo_empty),
        style = HomeAssistantGlanceTypography.bodyMedium,
        modifier = GlanceModifier.padding(all = 16.dp),
    )
}

@Composable
private fun ShowClimateContent(
    climateTemp: Float?,
    currentTemp: Float?,
    displayComplete: Boolean,
    onActionPlus: () -> Unit,
    onActionMinus: () -> Unit
) {
    Column(
        modifier = GlanceModifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            modifier = GlanceModifier.padding(16.dp),
            text = climateTemp?.toString() ?: "Empty",          // TODO: formatear temp
            style = HomeAssistantGlanceTypography.titleLarge.copy(fontWeight = FontWeight.Bold)
        )

        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            SquareIconButton(
                modifier = GlanceModifier.size(HomeAssistantGlanceTheme.dimensions.iconSize).semantics { testTag = "Add" },
                imageProvider = ImageProvider(androidx.media3.session.R.drawable.media3_icon_minus),    // TODO: ajustar ICON, no viene de donde viene el resto
                contentDescription = LocalContext.current.getString(commonR.string.widget_todo_add),    // TODO: ajustar string description
                backgroundColor = GlanceTheme.colors.primary,
                enabled = climateTemp != null,
                onClick = onActionMinus //actionOpenTodolist(listEntityId, serverId), // TODO agregar actions para climate
            )

            Spacer(GlanceModifier.width(16.dp))

            SquareIconButton(
                modifier = GlanceModifier.size(HomeAssistantGlanceTheme.dimensions.iconSize).semantics { testTag = "Add" },
                imageProvider = ImageProvider(R.drawable.ic_plus),
                contentDescription = LocalContext.current.getString(commonR.string.widget_todo_add),    // TODO: ajustar string description
                backgroundColor = GlanceTheme.colors.primary,
                enabled = climateTemp != null,
                onClick = onActionPlus //actionOpenTodolist(listEntityId, serverId), // TODO agregar actions para climate
            )
        }
    }
}

@Composable
private fun TitleBar(climateTemp: Float?, currentTemp: Float?, serverId: Int, outOfSync: Boolean) {
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
            onClick = {} // actionRefreshTodo(), // TODO agregar actions para climate
        )
    }
}

@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(250, 320)
@Composable
private fun ScreenPreview() {
    HomeAssistantGlanceTheme {
        ScreenForState(
            ClimateStateWithData(
                backgroundType = WidgetBackgroundType.DYNAMICCOLOR,
                textColor = null,
                serverId = 1,
                currentTemp = null,
                climateTemp = 12f,
                outOfSync = false,
                showComplete = true,
            ),
        )
    }
}

@OptIn(ExperimentalGlancePreviewApi::class)
@Preview
@Composable
private fun ScreenPreviewEmptyItems() {
    HomeAssistantGlanceTheme {
        ScreenForState(
            ClimateStateWithData(
                backgroundType = WidgetBackgroundType.DYNAMICCOLOR,
                textColor = null,
                serverId = 1,
                currentTemp = null,
                climateTemp = null,
                outOfSync = false,
                showComplete = true,
            ),
        )
    }
}

@OptIn(ExperimentalGlancePreviewApi::class)
@Preview
@Composable
private fun ScreenPreviewOutOfSync() {
    HomeAssistantGlanceTheme {
        ScreenForState(
            ClimateStateWithData(
                backgroundType = WidgetBackgroundType.DYNAMICCOLOR,
                textColor = null,
                serverId = 1,
                currentTemp = null,
                climateTemp = 12f,
                outOfSync = true,
                showComplete = true,
            ),
        )
    }
}

@OptIn(ExperimentalGlancePreviewApi::class)
@Preview
@Composable
private fun ScreenPreviewEmpty() {
    HomeAssistantGlanceTheme {
        ScreenForState(EmptyClimateState)
    }
}

@OptIn(ExperimentalGlancePreviewApi::class)
@Preview
@Composable
private fun ScreenPreviewLoading() {
    HomeAssistantGlanceTheme {
        ScreenForState(LoadingClimateState)
    }
}
