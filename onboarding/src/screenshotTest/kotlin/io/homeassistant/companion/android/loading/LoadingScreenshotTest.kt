package io.homeassistant.companion.android.loading

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.android.tools.screenshot.PreviewTest
import io.homeassistant.companion.android.compose.HAThemeScreenshot

class LoadingScreenshotTest {

    @PreviewTest
    @PreviewLightDark
    @Composable
    fun `LoadingScreen`() {
        HAThemeScreenshot {
            LoadingScreen(modifier = Modifier)
        }
    }
}
