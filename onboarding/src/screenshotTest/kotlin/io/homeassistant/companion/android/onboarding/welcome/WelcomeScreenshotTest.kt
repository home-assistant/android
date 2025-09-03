package io.homeassistant.companion.android.onboarding.welcome

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import io.homeassistant.companion.android.compose.HAPreviews
import io.homeassistant.companion.android.compose.HAThemeScreenshot

class WelcomeScreenshotTest {

    @PreviewTest
    @HAPreviews
    @Composable
    fun `WelcomeScreen`() {
        HAThemeScreenshot {
            WelcomeScreen(onConnectClick = {}, onLearnMoreClick = {})
        }
    }
}
