package io.homeassistant.companion.android

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayWithoutContext
import io.homeassistant.companion.android.common.data.websocket.impl.entities.GetTodosResponse.TodoItem
import io.homeassistant.companion.android.home.TodoListViewModel
import io.homeassistant.companion.android.home.views.TodoListView
import io.homeassistant.companion.android.home.views.TodoListsView
import io.homeassistant.companion.android.util.previewEntity1
import io.homeassistant.companion.android.util.previewEntity2

// A tall screen so the whole scrolling list is rendered, a round watch face only shows its first rows
private const val PREVIEW_WIDTH_DP = 227
private const val PREVIEW_HEIGHT_DP = 600

class TodoListViewPreviewsTest {

    private val groceries = EntityDisplayWithoutContext(previewEntity1, name = "Groceries")
    private val chores = EntityDisplayWithoutContext(previewEntity2, name = "Chores")

    @PreviewTest
    @Preview(
        widthDp = PREVIEW_WIDTH_DP,
        heightDp = PREVIEW_HEIGHT_DP,
        showBackground = true,
        backgroundColor = 0xFF000000,
    )
    @Composable
    private fun PreviewTodoListsView() {
        TodoListsView(
            lists = listOf(groceries, chores),
            status = TodoListViewModel.ListsStatus.Loaded,
            onSelectList = {},
            onRetry = {},
        )
    }

    @PreviewTest
    @Preview(
        widthDp = PREVIEW_WIDTH_DP,
        heightDp = PREVIEW_HEIGHT_DP,
        showBackground = true,
        backgroundColor = 0xFF000000,
    )
    @Composable
    private fun PreviewTodoListsViewError() {
        TodoListsView(
            lists = emptyList(),
            status = TodoListViewModel.ListsStatus.Error,
            onSelectList = {},
            onRetry = {},
        )
    }

    @PreviewTest
    @Preview(
        widthDp = PREVIEW_WIDTH_DP,
        heightDp = PREVIEW_HEIGHT_DP,
        showBackground = true,
        backgroundColor = 0xFF000000,
    )
    @Composable
    private fun PreviewTodoListWithCompletedItems() {
        TodoListView(
            listName = "Groceries",
            state = TodoListViewModel.UiState(
                entityId = groceries.entityId,
                items = listOf(
                    TodoItem(uid = "1", summary = "Milk", status = TodoItem.NEEDS_ACTION_STATUS),
                    TodoItem(uid = "2", summary = "Bread", status = TodoItem.NEEDS_ACTION_STATUS),
                    TodoItem(uid = "3", summary = "Eggs", status = TodoItem.COMPLETED_STATUS),
                ),
            ),
            onSetCompleted = { _, _ -> },
            onAddItem = {},
            onRemoveItem = {},
            onClearCompleted = {},
            onRetry = {},
        )
    }

    @PreviewTest
    @Preview(
        widthDp = PREVIEW_WIDTH_DP,
        heightDp = PREVIEW_HEIGHT_DP,
        showBackground = true,
        backgroundColor = 0xFF000000,
    )
    @Composable
    private fun PreviewTodoListEmpty() {
        TodoListView(
            listName = "Groceries",
            state = TodoListViewModel.UiState(entityId = groceries.entityId),
            onSetCompleted = { _, _ -> },
            onAddItem = {},
            onRemoveItem = {},
            onClearCompleted = {},
            onRetry = {},
        )
    }

    @PreviewTest
    @Preview(
        widthDp = PREVIEW_WIDTH_DP,
        heightDp = PREVIEW_HEIGHT_DP,
        showBackground = true,
        backgroundColor = 0xFF000000,
    )
    @Composable
    private fun PreviewTodoListLoadError() {
        TodoListView(
            listName = "Groceries",
            state = TodoListViewModel.UiState(entityId = groceries.entityId, hasLoadError = true),
            onSetCompleted = { _, _ -> },
            onAddItem = {},
            onRemoveItem = {},
            onClearCompleted = {},
            onRetry = {},
        )
    }
}
