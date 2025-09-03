package io.homeassistant.companion.android.frontend

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.android.tools.screenshot.PreviewTest
import io.homeassistant.companion.android.compose.HAPreviews
import io.homeassistant.companion.android.compose.HAThemeScreenshot

class FrontendScreenshotTest {
    @PreviewTest
    @HAPreviews
    @Composable
    fun `FrontendScreen`() {
        HAThemeScreenshot {
            FrontendScreen(modifier = Modifier)
        }
    }
}
