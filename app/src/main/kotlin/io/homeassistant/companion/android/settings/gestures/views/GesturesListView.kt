package io.homeassistant.companion.android.settings.gestures.views

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.homeassistant.companion.android.common.R
import io.homeassistant.companion.android.common.util.GestureAction
import io.homeassistant.companion.android.common.util.HAGesture
import io.homeassistant.companion.android.settings.views.SettingsRow
import io.homeassistant.companion.android.settings.views.SettingsSubheader
import io.homeassistant.companion.android.util.plus
import io.homeassistant.companion.android.util.safeBottomPaddingValues

/**
 * Shows all gestures with the configured action for it. Clicking on a
 * gesture calls [onGestureClicked] with the gesture.
 *
 * @param gestureActions User settings (gestures and the current action)
 * @param onGestureClicked Called when a gesture is selected
 */
@Composable
fun GesturesListView(gestureActions: Map<HAGesture, GestureAction>, onGestureClicked: (HAGesture) -> Unit) {
    LazyColumn(
        contentPadding = PaddingValues(vertical = 16.dp) + safeBottomPaddingValues(applyHorizontal = false),
    ) {
        item {
            Text(
                text = stringResource(R.string.gestures_description),
                modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp),
            )
        }

        val gesturesGrouped = HAGesture.entries.groupBy { it.direction }
        gesturesGrouped.forEach { (direction, gestures) ->
            item {
                SettingsSubheader(stringResource(direction.description))
            }
            items(gestures) { gesture ->
                val action = gestureActions[gesture]
                SettingsRow(
                    primaryText = stringResource(gesture.pointers.description),
                    secondaryText = action?.let { stringResource(it.description) } ?: "",
                    icon = null,
                    onClicked = { onGestureClicked(gesture) },
                )
            }
        }
    }
}

@Preview
@Composable
private fun PreviewGesturesListView() {
    GesturesListView(
        gestureActions = HAGesture.entries.associateWith { GestureAction.NONE },
        onGestureClicked = { _ -> },
    )
}
