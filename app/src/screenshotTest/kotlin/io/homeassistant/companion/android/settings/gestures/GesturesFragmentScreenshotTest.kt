package io.homeassistant.companion.android.settings.gestures

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import io.homeassistant.companion.android.common.util.GestureAction
import io.homeassistant.companion.android.common.util.HAGesture
import io.homeassistant.companion.android.settings.gestures.views.GestureActionsView
import io.homeassistant.companion.android.settings.gestures.views.GesturesListView
import io.homeassistant.companion.android.util.compose.HomeAssistantAppTheme

class GesturesFragmentScreenshotTest {

    @PreviewTest
    @Preview
    @Composable
    fun `Gestures list with no action for each gesture`() {
        HomeAssistantAppTheme {
            GesturesListView(
                gestureActions = HAGesture.entries.associateWith { GestureAction.NONE },
                onGestureClicked = { _ -> },
            )
        }
    }

    @PreviewTest
    @Preview
    @Composable
    fun `Gesture actions with search entities selected`() {
        HomeAssistantAppTheme {
            GestureActionsView(
                selectedAction = GestureAction.QUICKBAR_DEFAULT,
                onActionClicked = {},
            )
        }
    }
}
