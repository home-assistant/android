package io.homeassistant.companion.android.compose

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.navigation.compose.rememberNavController
import com.android.tools.screenshot.PreviewTest
import io.homeassistant.companion.android.common.compose.theme.HATheme

class HAAppScreenshotTest {

    @PreviewTest
    @PreviewLightDark
    @Composable
    fun `HAApp initial state`() {
        HATheme {
            HAApp(navController = rememberNavController())
        }
    }
}
