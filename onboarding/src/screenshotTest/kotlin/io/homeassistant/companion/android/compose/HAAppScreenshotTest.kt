package io.homeassistant.companion.android.compose

import androidx.compose.runtime.Composable
import androidx.navigation.compose.rememberNavController
import com.android.tools.screenshot.PreviewTest
import io.homeassistant.companion.android.common.compose.theme.HATheme

class HAAppScreenshotTest {

    @PreviewTest
    @HAPreviews
    @Composable
    fun `HAApp no start destination`() {
        HATheme {
            HAApp(navController = rememberNavController(), startDestination = null)
        }
    }
}
