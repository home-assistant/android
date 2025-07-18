package io.homeassistant.companion.android.settings.gestures.views

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Divider
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import io.homeassistant.companion.android.common.R
import io.homeassistant.companion.android.common.util.GestureAction
import io.homeassistant.companion.android.common.util.HAGesture
import io.homeassistant.companion.android.settings.views.SettingsRow
import io.homeassistant.companion.android.settings.views.SettingsSubheader
import io.homeassistant.companion.android.util.plus
import io.homeassistant.companion.android.util.safeBottomPaddingValues

@Composable
fun GesturesView(gestureActions: Map<HAGesture, GestureAction>, onSetAction: (HAGesture, GestureAction) -> Unit) {
    LazyColumn(
        contentPadding = PaddingValues(vertical = 16.dp) + safeBottomPaddingValues(applyHorizontal = false),
    ) {
        item {
            Text(
                text = stringResource(R.string.gestures_description),
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Divider(
                modifier = Modifier.padding(all = 16.dp),
            )
        }

        val gesturesGrouped = HAGesture.entries.groupBy { it.direction }
        gesturesGrouped.forEach { (direction, gestures) ->
            item {
                SettingsSubheader(stringResource(direction.description))
            }
            items(gestures) { gesture ->
                GestureSettingRow(
                    gesture = gesture,
                    action = gestureActions[gesture],
                    onSetAction = { action -> onSetAction(gesture, action) },
                )
            }
        }
    }
}

@Composable
private fun GestureSettingRow(gesture: HAGesture, action: GestureAction?, onSetAction: (GestureAction) -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        SettingsRow(
            primaryText = stringResource(gesture.pointers.description),
            secondaryText = action?.let { stringResource(it.description) } ?: "",
            icon = null,
            onClicked = { expanded = true },
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            // Prevent dropdown menu hitting screen edge which looks wrong
            offset = DpOffset(x = 8.dp, y = 0.dp),
        ) {
            GestureAction.entries.forEach { action ->
                DropdownMenuItem({
                    onSetAction(action)
                    expanded = false
                }) {
                    Text(stringResource(action.description))
                }
            }
        }
    }
}

@Preview
@Composable
private fun PreviewGesturesView() {
    GesturesView(
        gestureActions = HAGesture.entries.associateWith { GestureAction.NONE },
        onSetAction = { _, _ -> },
    )
}
