package io.homeassistant.companion.android.settings.gestures

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import io.homeassistant.companion.android.common.util.GestureAction
import io.homeassistant.companion.android.common.util.HAGesture
import io.homeassistant.companion.android.settings.gestures.views.GestureActionsView
import io.homeassistant.companion.android.settings.gestures.views.GesturesListView

class GesturesFragmentScreenshotTest {

    @Preview
    @Composable
    fun `Gestures list with no action for each gesture`() {
        GesturesListView(
            gestureActions = HAGesture.entries.associateWith { GestureAction.NONE },
            onGestureClicked = { _ -> },
        )
    }

    @Preview
    @Composable
    fun `Gesture actions with search entities selected`() {
        GestureActionsView(
            selectedAction = GestureAction.QUICKBAR_DEFAULT,
            onActionClicked = {},
        )
    }
}
