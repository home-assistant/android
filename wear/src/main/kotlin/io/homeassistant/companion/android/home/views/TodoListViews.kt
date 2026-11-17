package io.homeassistant.companion.android.home.views

import android.app.Activity
import android.app.RemoteInput
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.CheckboxButton
import androidx.wear.compose.material3.SwipeToReveal
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.rememberRevealState
import androidx.wear.input.RemoteInputIntentHelper
import androidx.wear.tooling.preview.devices.WearDevices
import io.github.timoptr.mdiicons.Mdi
import io.github.timoptr.mdiicons.generated.Delete
import io.github.timoptr.mdiicons.rememberImageVector
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplay
import io.homeassistant.companion.android.common.data.websocket.impl.entities.GetTodosResponse.TodoItem
import io.homeassistant.companion.android.home.TodoListViewModel
import io.homeassistant.companion.android.theme.WearAppTheme
import io.homeassistant.companion.android.theme.getFilledTonalButtonColors
import io.homeassistant.companion.android.views.ListHeader
import io.homeassistant.companion.android.views.ThemeLazyColumn

private const val REMOTE_INPUT_KEY = "todo_item"

/** The to-do lists (entities of the `todo` domain) of the server, to pick one to open. */
@Composable
fun TodoListsView(
    lists: List<EntityDisplay>,
    status: TodoListViewModel.ListsStatus,
    onSelectList: (entityId: String) -> Unit,
    onRetry: () -> Unit,
) {
    WearAppTheme {
        ThemeLazyColumn {
            item { ListHeader(id = commonR.string.todo_lists) }
            if (lists.isEmpty()) {
                item {
                    when (status) {
                        TodoListViewModel.ListsStatus.LOADING -> Text(
                            text = stringResource(commonR.string.loading),
                            textAlign = TextAlign.Center,
                        )
                        TodoListViewModel.ListsStatus.ERROR -> Button(
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(commonR.string.todo_list_load_failed)) },
                            secondaryLabel = { Text(stringResource(commonR.string.refresh)) },
                            onClick = onRetry,
                            colors = getFilledTonalButtonColors(),
                        )
                        TodoListViewModel.ListsStatus.LOADED -> Text(
                            text = stringResource(commonR.string.todo_lists_empty),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            } else {
                items(lists, key = { it.entityId }) { list ->
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(list.name) },
                        onClick = { onSelectList(list.entityId) },
                        colors = getFilledTonalButtonColors(),
                    )
                }
            }
        }
    }
}

/**
 * The items of one to-do list: tap to complete or reopen, swipe to remove, a button to clear the completed items, and a button to add
 * an item with the keyboard or voice input of the watch.
 */
@Composable
fun TodoListView(
    listName: String,
    state: TodoListViewModel.UiState,
    onSetCompleted: (TodoItem, Boolean) -> Unit,
    onAddItem: (String) -> Unit,
    onRemoveItem: (TodoItem) -> Unit,
    onClearCompleted: () -> Unit,
    onRetry: () -> Unit,
) {
    val addLabel = stringResource(commonR.string.todo_list_new_item_hint)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            RemoteInput.getResultsFromIntent(result.data)?.getCharSequence(REMOTE_INPUT_KEY)?.let {
                onAddItem(it.toString())
            }
        }
    }
    val (open, completed) = state.items.partition { it.status != TodoItem.COMPLETED_STATUS }

    WearAppTheme {
        ThemeLazyColumn {
            item { ListHeader(listName) }
            item {
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(commonR.string.todo_list_add_item)) },
                    onClick = {
                        val intent = RemoteInputIntentHelper.createActionRemoteInputIntent()
                        RemoteInputIntentHelper.putRemoteInputsExtra(
                            intent,
                            listOf(RemoteInput.Builder(REMOTE_INPUT_KEY).setLabel(addLabel).build()),
                        )
                        launcher.launch(intent)
                    },
                    colors = getFilledTonalButtonColors(),
                )
            }
            if (state.hasActionError) {
                item {
                    Text(
                        text = stringResource(commonR.string.todo_list_action_failed),
                        textAlign = TextAlign.Center,
                    )
                }
            }
            if (state.hasLoadError) {
                item {
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(commonR.string.todo_list_load_failed)) },
                        secondaryLabel = { Text(stringResource(commonR.string.refresh)) },
                        onClick = onRetry,
                        colors = getFilledTonalButtonColors(),
                    )
                }
            }
            items(open, key = { "open.${it.uid ?: it.summary}" }) { item ->
                TodoItemRow(item, checked = false, onSetCompleted = onSetCompleted, onRemove = onRemoveItem)
            }
            if (completed.isNotEmpty()) {
                item { ListHeader(commonR.string.todo_list_completed) }
                item {
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(commonR.string.todo_list_clear_completed)) },
                        onClick = onClearCompleted,
                        colors = getFilledTonalButtonColors(),
                    )
                }
                items(completed, key = { "done.${it.uid ?: it.summary}" }) { item ->
                    TodoItemRow(item, checked = true, onSetCompleted = onSetCompleted, onRemove = onRemoveItem)
                }
            }
        }
    }
}

@Composable
private fun TodoItemRow(
    item: TodoItem,
    checked: Boolean,
    onSetCompleted: (TodoItem, Boolean) -> Unit,
    onRemove: (TodoItem) -> Unit,
) {
    val revealState = rememberRevealState()
    SwipeToReveal(
        primaryAction = {
            PrimaryActionButton(
                onClick = { onRemove(item) },
                icon = { Image(imageVector = Mdi.Delete.rememberImageVector(), contentDescription = null) },
                text = { Text(stringResource(commonR.string.todo_list_remove_item)) },
            )
        },
        onSwipePrimaryAction = { onRemove(item) },
        revealState = revealState,
    ) {
        CheckboxButton(
            modifier = Modifier.fillMaxWidth(),
            checked = checked,
            onCheckedChange = { onSetCompleted(item, it) },
            label = {
                Text(
                    text = item.summary.orEmpty(),
                    maxLines = 3,
                    textDecoration = if (checked) TextDecoration.LineThrough else null,
                )
            },
        )
    }
}

@Preview(device = WearDevices.LARGE_ROUND)
@Composable
private fun PreviewTodoListView() {
    TodoListView(
        listName = "Groceries",
        state = TodoListViewModel.UiState(
            entityId = "todo.groceries",
            items = listOf(
                TodoItem("1", "Milk", TodoItem.NEEDS_ACTION_STATUS),
                TodoItem("2", "Bread", TodoItem.NEEDS_ACTION_STATUS),
                TodoItem("3", "Eggs", TodoItem.COMPLETED_STATUS),
            ),
        ),
        onSetCompleted = { _, _ -> },
        onAddItem = {},
        onRemoveItem = {},
        onClearCompleted = {},
        onRetry = {},
    )
}

@Preview(device = WearDevices.LARGE_ROUND)
@Composable
private fun PreviewTodoListsViewEmpty() {
    TodoListsView(
        lists = emptyList(),
        status = TodoListViewModel.ListsStatus.LOADED,
        onSelectList = {},
        onRetry = {},
    )
}
