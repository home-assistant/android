package io.homeassistant.companion.android.onboarding.welcome

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.android.tools.screenshot.PreviewTest
import io.homeassistant.companion.android.compose.HAThemeScreenshot

class WelcomeScreenshotTest {

    @PreviewTest
    @PreviewLightDark
    @Composable
    fun `WelcomeScreen`() {
        HAThemeScreenshot {
            WelcomeScreen(onConnectClick = {}, onLearnMoreClick = {})
        }
    }
}
