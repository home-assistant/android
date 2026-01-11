package io.homeassistant.companion.android.widgets.grid

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.CircularProgressIndicator
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.components.CircleIconButton
import androidx.glance.appwidget.components.Scaffold
import androidx.glance.appwidget.components.TitleBar
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.GridCells
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.LazyVerticalGrid
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.lazy.itemsIndexed
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.preview.ExperimentalGlancePreviewApi
import androidx.glance.preview.Preview
import androidx.glance.semantics.semantics
import androidx.glance.semantics.testTag
import androidx.glance.text.Text
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import dagger.hilt.EntryPoint
import dagger.hilt.EntryPoints
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.util.compose.HomeAssistantGlanceTheme
import io.homeassistant.companion.android.util.compose.HomeAssistantGlanceTypography
import io.homeassistant.companion.android.util.compose.glanceStringResource
import io.homeassistant.companion.android.util.icondialog.getIconByMdiName
import io.homeassistant.companion.android.widgets.grid.GridWidgetLayoutSize.Companion.gridCells
import io.homeassistant.companion.android.widgets.grid.GridWidgetLayoutSize.Companion.showTitleBar
import io.homeassistant.companion.android.widgets.grid.GridWidgetState.Companion.getButtonColors
import io.homeassistant.companion.android.widgets.grid.GridWidgetState.Companion.getColors
import kotlin.math.ceil

class GridGlanceAppWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    internal interface GridWidgetEntryPoint {
        fun gridWidgetStateUpdater(): GridWidgetStateUpdater
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val manager = GlanceAppWidgetManager(context)
        val widgetId = manager.getAppWidgetId(id)

        provideContent {
            val entryPoints = remember { EntryPoints.get(context, GridWidgetEntryPoint::class.java) }
            val flow = remember { entryPoints.gridWidgetStateUpdater().stateFlow(widgetId) }

            val state by flow.collectAsState(LoadingGridState)

            HomeAssistantGlanceTheme(
                colors = state.getColors(),
            ) {
                GridWidgetContent(state)
            }
        }
    }
}

@Composable
private fun GlanceModifier.gridWidgetBackground(): GlanceModifier = this
    .appWidgetBackground()
    .fillMaxSize()
    .background(GlanceTheme.colors.widgetBackground)

@Composable
@VisibleForTesting
internal fun GridWidgetContent(state: GridWidgetState) {
    when (state) {
        is LoadingGridState -> LoadingScreen()
        is GridStateWithData -> GridContentWithData(state)
    }
}

@Composable
private fun LoadingScreen() {
    Column(
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = GlanceModifier.gridWidgetBackground().semantics { testTag = "LoadingScreen" },
    ) {
        CircularProgressIndicator(
            color = GlanceTheme.colors.primary,
            modifier = GlanceModifier.size(HomeAssistantGlanceTheme.dimensions.iconSize),
        )
    }
}

@Composable
private fun GridContentWithData(state: GridStateWithData, modifier: GlanceModifier = GlanceModifier) {
    fun titleBar(): @Composable (() -> Unit) = {
        state.label?.let { label ->
            TitleBar(
                startIcon = ImageProvider(R.drawable.ic_home_variant_outline),
                title = label,
                actions = {
                    CircleIconButton(
                        imageProvider = ImageProvider(R.drawable.ic_refresh),
                        contentDescription = glanceStringResource(commonR.string.refresh),
                        onClick = actionRefreshGrid(),
                        backgroundColor = null,
                    )
                },
            )
        }
    }

    fun buttonProvider(): @Composable (GridButtonData) -> Unit = {
        GridButton(
            modifier = GlanceModifier.fillMaxSize(),
            label = it.label,
            icon = it.icon,
            state = it.state,
            isActive = it.isActive,
            buttonColors = state.getButtonColors(),
            onClick = actionPressEntity(it.id),
        )
    }

    val scaffoldTopPadding = if (showTitleBar()) 0.dp else 12.dp

    Scaffold(
        modifier = modifier
            .gridWidgetBackground()
            .padding(top = scaffoldTopPadding),
        titleBar = if (showTitleBar() && state.label != null) titleBar() else null,
    ) {
        val gridCells = gridCells()
        if (gridCells == 1) {
            LazyButtonColumn(
                modifier = GlanceModifier.fillMaxSize(),
                items = state.items,
                itemContentProvider = buttonProvider(),
            )
        } else {
            LazyButtonGrid(
                modifier = GlanceModifier.fillMaxSize(),
                gridCells = gridCells(),
                items = state.items,
                itemContentProvider = buttonProvider(),
            )
        }
    }
}

/**
 * Size of the widget per the reference breakpoints. Each size has its own display
 * characteristics such as - showing content as list vs grid, font sizes etc.
 *
 * In this layout, only width breakpoints are used to scale the layout.
 */
private enum class GridWidgetLayoutSize(val maxWidth: Dp) {
    // Compact buttons, no title bar
    Small(maxWidth = 180.dp),

    // Icon and text
    Normal(maxWidth = 439.dp),
    ;

    companion object {
        /**
         * Returns the corresponding [GridWidgetLayoutSize] to be considered for the current
         * widget size.
         */
        @Composable
        fun fromLocalSize(): GridWidgetLayoutSize {
            val width = LocalSize.current.width
            return if (width >= Small.maxWidth) Normal else Small
        }

        @Composable
        fun showTitleBar(): Boolean {
            return fromLocalSize() == Normal && LocalSize.current.height >= 180.dp
        }

        @Composable
        fun gridCells(): Int {
            val width = LocalSize.current.width
            return when (width) {
                in 0.dp..Small.maxWidth -> ceil(width / 96.dp).toInt()
                else -> ceil(width / 260.dp).toInt()
            }
        }
    }
}

@Composable
private fun LazyButtonColumn(
    items: List<GridButtonData>,
    itemContentProvider: @Composable (item: GridButtonData) -> Unit,
    modifier: GlanceModifier = GlanceModifier,
) {
    LazyColumn(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        items(items) {
            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .padding(bottom = 8.dp),
            ) {
                itemContentProvider(it)
            }
        }
    }
}

@Composable
private fun LazyButtonGrid(
    gridCells: Int,
    items: List<GridButtonData>,
    itemContentProvider: @Composable (item: GridButtonData) -> Unit,
    modifier: GlanceModifier = GlanceModifier,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    cellSpacing: Dp = 12.dp,
) {
    val numRows = ceil(items.size.toDouble() / gridCells).toInt()

    // Cell spacing is achieved by allocating equal amount of padding to each cell. Cells on edge
    // apply it completely to inner sides, while cells not on edge apply it evenly on sides.
    val perCellHorizontalPadding = (cellSpacing * (gridCells - 1)) / gridCells
    val perCellVerticalPadding = (cellSpacing * (numRows - 1)) / numRows

    LazyVerticalGrid(
        modifier = modifier,
        gridCells = GridCells.Fixed(gridCells),
        horizontalAlignment = horizontalAlignment,
    ) {
        itemsIndexed(items) { index, item ->
            val row = index / gridCells
            val column = index % gridCells

            val cellTopPadding = when (row) {
                0 -> 0.dp
                numRows - 1 -> perCellVerticalPadding
                else -> perCellVerticalPadding / 2
            }

            val cellBottomPadding = when (row) {
                0 -> perCellVerticalPadding
                numRows - 1 -> perCellVerticalPadding
                else -> perCellVerticalPadding / 2
            }

            val cellStartPadding = when (column) {
                0 -> 0.dp
                gridCells - 1 -> perCellHorizontalPadding
                else -> perCellHorizontalPadding / 2
            }

            val cellEndPadding = when (column) {
                0 -> perCellHorizontalPadding
                gridCells - 1 -> 0.dp
                else -> perCellHorizontalPadding / 2
            }

            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .padding(
                        start = cellStartPadding,
                        end = cellEndPadding,
                        top = cellTopPadding,
                        bottom = cellBottomPadding,
                    ),
            ) {
                itemContentProvider(item)
            }
        }
    }
}

@Composable
private fun GridButton(
    label: String,
    onClick: Action,
    modifier: GlanceModifier = GlanceModifier,
    icon: String? = null,
    state: String? = null,
    isActive: Boolean = false,
    buttonColors: GridButtonColors = GridButtonColors.Default,
) {
    val size = GridWidgetLayoutSize.fromLocalSize()

    val isCompact = size == GridWidgetLayoutSize.Small
    val horizontalAlignment = if (isCompact) Alignment.CenterHorizontally else Alignment.Start
    val baseModifier = if (!isCompact) modifier.height(64.dp) else modifier
    val innerPadding = if (isCompact) 8.dp else 12.dp
    val backgroundColor = if (isActive) buttonColors.activeBackgroundColor else buttonColors.backgroundColor
    val contentColor = if (isActive) buttonColors.activeContentColor else buttonColors.contentColor

    Row(
        modifier = baseModifier
            .clickable(onClick)
            .cornerRadius(16.dp)
            .padding(innerPadding)
            .background(backgroundColor),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = horizontalAlignment,
    ) {
        icon?.let {
            GridButtonIcon(icon, colorFilter = ColorFilter.tint(contentColor))
        } ?: GridButtonIconFallback(label)

        if (!isCompact) {
            Spacer(GlanceModifier.width(8.dp))
            Column {
                Text(
                    text = label,
                    style = HomeAssistantGlanceTypography.bodyLarge.copy(color = contentColor),
                    maxLines = 1,
                )
                Text(
                    text = state ?: "—",
                    style = HomeAssistantGlanceTypography.bodySmall.copy(color = contentColor),
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun GridButtonIcon(icon: String, modifier: GlanceModifier = GlanceModifier, colorFilter: ColorFilter? = null) {
    val iconProvider = rememberMdiIconProvider(icon)
    Image(
        modifier = modifier.size(24.dp),
        provider = iconProvider,
        contentDescription = null,
        colorFilter = colorFilter,
    )
}

@Composable
private fun GridButtonIconFallback(name: String, modifier: GlanceModifier = GlanceModifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Text(name.first().toString(), style = HomeAssistantGlanceTypography.titleLarge)
    }
}

@Composable
private fun rememberMdiIconProvider(name: String): ImageProvider {
    val context = LocalContext.current
    return remember(name) {
        val bitmap = CommunityMaterial.getIconByMdiName(name)?.let {
            IconicsDrawable(context, it).toBitmap()
        }
        bitmap?.let { ImageProvider(bitmap) } ?: ImageProvider(commonR.drawable.ic_priority)
    }
}

@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 96, heightDp = 200)
@Preview(widthDp = 259, heightDp = 200)
@Preview(widthDp = 259, heightDp = 120)
@Preview(widthDp = 438, heightDp = 200)
@Preview(widthDp = 644, heightDp = 200)
private annotation class GridWidgetBreakpointPreviews

@OptIn(ExperimentalGlancePreviewApi::class)
@Composable
@GridWidgetBreakpointPreviews
private fun DynamicGridWidgetPreview() {
    HomeAssistantGlanceTheme(GlanceTheme.colors) {
        GridContentWithData(
            modifier = GlanceModifier.cornerRadius(32.dp),
            state = GridStateWithData(
                label = "Home",
                items = listOf(
                    GridButtonData("0", "Living room", "mdi:lightbulb", "Off"),
                    GridButtonData("1", "Bedside table lamp", "mdi:lamp", "On", true),
                    GridButtonData("2", "Bedroom temperature", "mdi:thermometer", "15 ºC"),
                    GridButtonData("3", "Main entrance", "mdi:gesture-tap-button", "5 min ago"),
                    GridButtonData("4", "Test 5", "mdi:aaa"),
                ),
            ),
        )
    }
}
