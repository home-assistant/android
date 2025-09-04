package io.homeassistant.companion.android.widgets.todo

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
import androidx.glance.appwidget.CheckBox
import androidx.glance.appwidget.CircularProgressIndicator
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.components.CircleIconButton
import androidx.glance.appwidget.components.Scaffold
import androidx.glance.appwidget.components.SquareIconButton
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
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
import androidx.glance.text.TextDecoration
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
import io.homeassistant.companion.android.widgets.todo.TodoState.Companion.getColors

/**
 * Glance widget for managing and displaying a Todo List.
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
class TodoGlanceAppWidget : GlanceAppWidget() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    internal interface TodoGlanceWidgetEntryPoint {
        fun stateUpdater(): TodoWidgetStateUpdater
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val manager = GlanceAppWidgetManager(context)
        val widgetId = manager.getAppWidgetId(id)

        provideContent {
            val entryPoints = remember { EntryPoints.get(context, TodoGlanceWidgetEntryPoint::class.java) }
            val flow = remember { entryPoints.stateUpdater().stateFlow(widgetId) }

            val state by flow.collectAsState(LoadingTodoState)

            HomeAssistantGlanceTheme(
                colors = state.getColors(),
            ) {
                ScreenForState(state)
            }
        }
    }
}

@Composable
private fun GlanceModifier.todoWidgetBackground(): GlanceModifier {
    return this.appWidgetBackground().fillMaxSize().background(
        GlanceTheme
            .colors.widgetBackground,
    )
}

@Composable
@VisibleForTesting
internal fun ScreenForState(state: TodoState) {
    when (state) {
        LoadingTodoState -> LoadingScreen()
        EmptyTodoState -> EmptyScreen()
        is TodoStateWithData -> Screen(state)
    }
}

@Composable
private fun LoadingScreen() {
    Column(
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = GlanceModifier.todoWidgetBackground().semantics { testTag = "LoadingScreen" },
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
        modifier = GlanceModifier.todoWidgetBackground().semantics { testTag = "EmptyScreen" },
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
private fun Screen(state: TodoStateWithData) {
    Scaffold(
        titleBar = {
            TitleBar(
                listName = state.listName,
                serverId = state.serverId,
                listEntityId = state.listEntityId,
                outOfSync = state.outOfSync,
            )
        },
        // We manually set the padding on each item since the checkbox comes with an embedded padding that
        // we cannot modify.
        horizontalPadding = 0.dp,
        modifier = GlanceModifier.todoWidgetBackground().semantics { testTag = "Screen" },
    ) {
        if (state.hasDisplayableItems()) {
            ListContent(state.todoItems, state.showComplete)
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
private fun ListContent(todoItems: List<TodoItemState>, displayComplete: Boolean) {
    LazyColumn {
        if (todoItems.any { !it.done }) {
            todoItems.filter { !it.done }.forEach {
                item { TodoItem(it) }
            }
        }
        if (displayComplete && todoItems.any { it.done }) {
            todoItems.filter { it.done }.forEach {
                item { TodoItem(it) }
            }
        }
    }
}

@Composable
private fun TitleBar(listName: String?, serverId: Int, listEntityId: String, outOfSync: Boolean) {
    Row(
        // Try to align the paddings with Google Calendar widget
        modifier = GlanceModifier.padding(top = 12.dp, end = 12.dp, start = 16.dp).fillMaxWidth(),
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        Text(
            text = listName ?: glanceStringResource(commonR.string.widget_todo_label),
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
            onClick = actionRefreshTodo(),
        )
        SquareIconButton(
            modifier = GlanceModifier.size(HomeAssistantGlanceTheme.dimensions.iconSize).semantics { testTag = "Add" },
            imageProvider = ImageProvider(R.drawable.ic_plus),
            contentDescription = LocalContext.current.getString(commonR.string.widget_todo_add),
            backgroundColor = GlanceTheme.colors.primary,
            onClick = actionOpenTodolist(listEntityId, serverId),
        )
    }
}

@Composable
private fun TodoItem(todoItem: TodoItemState) {
    var textStyle = HomeAssistantGlanceTypography.bodySmall
    if (todoItem.done) textStyle = textStyle.copy(textDecoration = TextDecoration.LineThrough)
    CheckBox(
        checked = todoItem.done,
        onCheckedChange = actionToggleTodo(todoItem),
        text = todoItem.name,
        style = textStyle,
        // The checkbox comes with an embedded padding that we cannot modify of 6.5.dp we add 9.5.dp to align with the rest of the screen at 16dp.
        // 4.dp at the end is to avoid that the scrollbar go over the text
        modifier = GlanceModifier.padding(vertical = 8.dp).padding(start = 9.5.dp, end = 4.dp),
    )
}

@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(250, 320)
@Composable
private fun ScreenPreview() {
    HomeAssistantGlanceTheme {
        ScreenForState(
            TodoStateWithData(
                backgroundType = WidgetBackgroundType.DYNAMICCOLOR,
                textColor = null,
                serverId = 1,
                listEntityId = "",
                listName = "Shopping List",
                todoItems = listOf(
                    TodoItemState(null, "Eggs", true),
                    TodoItemState(null, "Milk", false),
                    TodoItemState(null, "Bread", false),
                ),
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
            TodoStateWithData(
                backgroundType = WidgetBackgroundType.DYNAMICCOLOR,
                textColor = null,
                serverId = 1,
                listEntityId = "",
                listName = "Test",
                todoItems = emptyList(),
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
            TodoStateWithData(
                backgroundType = WidgetBackgroundType.DYNAMICCOLOR,
                textColor = null,
                serverId = 1,
                listEntityId = "",
                listName = "Test",
                todoItems = emptyList(),
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
        ScreenForState(EmptyTodoState)
    }
}

@OptIn(ExperimentalGlancePreviewApi::class)
@Preview
@Composable
private fun ScreenPreviewLoading() {
    HomeAssistantGlanceTheme {
        ScreenForState(LoadingTodoState)
    }
}
