package io.homeassistant.companion.android

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import io.homeassistant.companion.android.common.compose.theme.HATheme

class LauncherScreenshotTest {

    @PreviewTest
    @Preview
    @Composable
    fun `Dummy Launcher Screenshot`() {
        HATheme { }
    }
}
