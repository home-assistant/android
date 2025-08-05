package io.homeassistant.companion.android.webview.addto

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import io.homeassistant.companion.android.util.compose.HomeAssistantAppTheme

class AddToDialogScreenshotTest {

    @Preview
    @Composable
    fun `AddToDialog with all add to options`() {
        HomeAssistantAppTheme {
            AddToBottomSheet(
                listOf(
                    AddToAction.MediaPlayerWidget,
                    AddToAction.TodoWidget,
                    AddToAction.EntityWidget,
                    AddToAction.CameraWidget,
                    AddToAction.Tile,
                    AddToAction.Shortcut,
                    AddToAction.Watch("Hello"),
                ),
            ) {}
        }
    }

    @Preview
    @Composable
    fun `AddToDialog with one options`() {
        HomeAssistantAppTheme {
            AddToBottomSheet(
                listOf(
                    AddToAction.MediaPlayerWidget,
                ),
            ) {}
        }
    }
}
