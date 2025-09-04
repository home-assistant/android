package io.homeassistant.companion.android.frontend

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.android.tools.screenshot.PreviewTest
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.compose.HAPreviews

class FrontendScreenshotTest {
    @PreviewTest
    @HAPreviews
    @Composable
    fun `FrontendScreen`() {
        HAThemeForPreview {
            FrontendScreen(modifier = Modifier)
        }
    }
}
