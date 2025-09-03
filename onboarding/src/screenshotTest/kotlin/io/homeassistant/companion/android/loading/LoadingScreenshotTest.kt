package io.homeassistant.companion.android.loading

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.android.tools.screenshot.PreviewTest
import io.homeassistant.companion.android.compose.HAPreviews
import io.homeassistant.companion.android.compose.HAThemeScreenshot

class LoadingScreenshotTest {

    @PreviewTest
    @HAPreviews
    @Composable
    fun `LoadingScreen`() {
        HAThemeScreenshot {
            LoadingScreen(modifier = Modifier)
        }
    }
}
