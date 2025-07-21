package io.homeassistant.companion.android.settings.gestures.views

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
import io.homeassistant.companion.android.util.safeBottomPaddingValues

@Composable
fun GestureActionsView(selectedAction: GestureAction, onActionSelected: (GestureAction) -> Unit) {
    val actionsGrouped = GestureAction.entries.minus(GestureAction.NONE).groupBy { it.category }
    LazyColumn(
        contentPadding = safeBottomPaddingValues(applyHorizontal = false),
        modifier = Modifier.selectableGroup(),
    ) {
        item {
            // GestureAction.NONE is here so it's always first in the list with no header
            GestureActionItem(
                action = GestureAction.NONE,
                checked = selectedAction == GestureAction.NONE,
                onClick = { onActionSelected(GestureAction.NONE) },
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
                    onClick = { onActionSelected(action) },
                )
            }
        }
    }
}

@Composable
fun GestureActionItem(action: GestureAction, checked: Boolean, onClick: () -> Unit) {
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
            style = MaterialTheme.typography.body1.merge(),
            modifier = Modifier.padding(start = 16.dp),
        )
    }
}

@Preview
@Composable
fun PreviewGestureActionsView() {
    GestureActionsView(
        selectedAction = GestureAction.QUICKBAR_DEFAULT,
        onActionSelected = {},
    )
}
