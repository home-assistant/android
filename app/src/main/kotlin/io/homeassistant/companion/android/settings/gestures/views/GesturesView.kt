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
import io.homeassistant.companion.android.common.util.FailFast
import io.homeassistant.companion.android.common.util.GestureAction
import io.homeassistant.companion.android.common.util.HAGesture
import io.homeassistant.companion.android.settings.views.SettingsRow
import io.homeassistant.companion.android.settings.views.SettingsSubheader
import io.homeassistant.companion.android.util.plus
import io.homeassistant.companion.android.util.safeBottomPaddingValues

@Composable
fun GesturesView(
    onGetAction: (HAGesture) -> GestureAction,
    onSetAction: (HAGesture, GestureAction) -> Unit,
) {
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
                    getAction = { onGetAction(gesture) },
                    onSetAction = { action -> onSetAction(gesture, action) },
                )
            }
        }
    }
}

@Composable
fun GestureSettingRow(
    gesture: HAGesture,
    getAction: () -> GestureAction,
    onSetAction: (GestureAction) -> Unit,
) {
    var currentAction by remember(gesture.name) { mutableStateOf(getAction()) }
    var expanded by remember { mutableStateOf(false) }

    Box {
        SettingsRow(
            primaryText = when (gesture.pointers) {
                2 -> stringResource(R.string.gestures_pointers_two)
                3 -> stringResource(R.string.gestures_pointers_three)
                else -> {
                    FailFast.fail { "Missing pointer count string for $gesture" }
                    gesture.pointers.toString()
                }
            },
            secondaryText = stringResource(currentAction.description),
            icon = null,
            onClicked = { expanded = true },
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            // Match text padding
            offset = DpOffset(x = 8.dp, y = 0.dp),
        ) {
            GestureAction.entries.forEach { action ->
                DropdownMenuItem({
                    onSetAction(action)
                    currentAction = action
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
fun PreviewGesturesView() {
    GesturesView(
        onGetAction = { _ -> GestureAction.NONE },
        onSetAction = { _, _ -> },
    )
}
