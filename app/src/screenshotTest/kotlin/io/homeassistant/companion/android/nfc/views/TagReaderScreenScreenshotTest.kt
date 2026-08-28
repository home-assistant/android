package io.homeassistant.companion.android.nfc.views

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.nfc.TagReaderUiState
import io.homeassistant.companion.android.util.compose.HAPreviews

class TagReaderScreenScreenshotTest {

    @PreviewTest
    @HAPreviews
    @Composable
    fun `TagReaderScreen approving`() {
        HAThemeForPreview {
            TagReaderScreen(
                state = TagReaderUiState.ApprovingTag("51f64799-2f18-4c6f-be65-48abcd5ea683"),
                onAllowOnce = {},
                onAllowAlways = {},
                onDismissed = {},
                onErrorAcknowledged = {},
                onFinished = {},
            )
        }
    }

    @PreviewTest
    @HAPreviews
    @Composable
    fun `TagReaderScreen scanning`() {
        HAThemeForPreview {
            TagReaderScreen(
                state = TagReaderUiState.Scanning,
                onAllowOnce = {},
                onAllowAlways = {},
                onDismissed = {},
                onErrorAcknowledged = {},
                onFinished = {},
            )
        }
    }
}
