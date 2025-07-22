package io.homeassistant.companion.android.settings.gestures.views

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.MaterialTheme
import androidx.compose.material.RadioButton
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.homeassistant.companion.android.common.util.GestureAction
import io.homeassistant.companion.android.settings.views.SettingsSubheader
import io.homeassistant.companion.android.util.plus
import io.homeassistant.companion.android.util.safeBottomPaddingValues

/**
 * View showing all actions for a gesture, grouped by category, and the currently
 * configured action. Clicking on an action calls [onActionClicked] with the action.
 *
 * @param selectedAction The current action
 * @param onActionClicked Called when an action is selected
 */
@Composable
fun GestureActionsView(selectedAction: GestureAction, onActionClicked: (GestureAction) -> Unit) {
    val actionsGrouped = GestureAction.entries.minus(GestureAction.NONE).groupBy { it.category }
    LazyColumn(
        contentPadding = PaddingValues(vertical = 16.dp) + safeBottomPaddingValues(applyHorizontal = false),
        modifier = Modifier.selectableGroup(),
    ) {
        item {
            // GestureAction.NONE is here so it's always first in the list with no header
            GestureActionItem(
                action = GestureAction.NONE,
                checked = selectedAction == GestureAction.NONE,
                onClick = { onActionClicked(GestureAction.NONE) },
            )
        }
        actionsGrouped.forEach { (category, actions) ->
            item {
                SettingsSubheader(stringResource(category.description))
            }
            items(actions) { action ->
                GestureActionItem(
                    action = action,
                    checked = selectedAction == action,
                    onClick = { onActionClicked(action) },
                )
            }
        }
    }
}

@Composable
private fun GestureActionItem(action: GestureAction, checked: Boolean, onClick: () -> Unit) {
    // Based on androidx.compose.material.samples.RadioGroupSample
    Row(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .selectable(
                selected = checked,
                onClick = onClick,
                role = Role.RadioButton,
            )
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = checked,
            onClick = null, // Handled by parent Row
        )
        Text(
            text = stringResource(action.description),
            style = MaterialTheme.typography.body1,
            modifier = Modifier.padding(start = 32.dp),
        )
    }
}

@Preview
@Composable
private fun PreviewGestureActionsView() {
    GestureActionsView(
        selectedAction = GestureAction.QUICKBAR_DEFAULT,
        onActionClicked = {},
    )
}
