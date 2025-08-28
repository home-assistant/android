package io.homeassistant.companion.android.frontend

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.android.tools.screenshot.PreviewTest
import io.homeassistant.companion.android.compose.HAThemeScreenshot

class FrontendScreenshotTest {
    @PreviewTest
    @PreviewLightDark
    @Composable
    fun `FrontendScreen`() {
        HAThemeScreenshot {
            FrontendScreen(modifier = Modifier)
        }
    }
}
