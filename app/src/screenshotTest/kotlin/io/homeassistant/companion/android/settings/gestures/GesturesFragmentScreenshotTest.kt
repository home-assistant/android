package io.homeassistant.companion.android.settings.gestures

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import io.homeassistant.companion.android.common.util.GestureAction
import io.homeassistant.companion.android.common.util.GesturePointers
import io.homeassistant.companion.android.common.util.HAGesture
import io.homeassistant.companion.android.settings.gestures.views.GestureActionsView
import io.homeassistant.companion.android.settings.gestures.views.GesturesListView
import io.homeassistant.companion.android.util.compose.HomeAssistantAppTheme

class GesturesFragmentScreenshotTest {

    @PreviewTest
    @Preview
    @Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
    @Composable
    fun `Gestures list with no action for each gesture`() {
        HomeAssistantAppTheme {
            GesturesListView(
                gestureActions = HAGesture.entries.associateWith { GestureAction.NONE },
                availableGestures = HAGesture.entries,
                onGestureClicked = { _ -> },
                modifier = Modifier.background(MaterialTheme.colors.background),
            )
        }
    }

    @PreviewTest
    @Preview
    @Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
    @Composable
    fun `Gestures list with system navigation gestures`() {
        HomeAssistantAppTheme {
            GesturesListView(
                gestureActions = HAGesture.entries.associateWith { GestureAction.NONE },
                availableGestures = HAGesture.entries.filter { it.pointers != GesturePointers.ONE },
                onGestureClicked = { _ -> },
                modifier = Modifier.background(MaterialTheme.colors.background),
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
