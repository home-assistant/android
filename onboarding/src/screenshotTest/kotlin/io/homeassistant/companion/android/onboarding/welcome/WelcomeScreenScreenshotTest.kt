package io.homeassistant.companion.android.onboarding.welcome

import androidx.compose.runtime.Composable
import io.homeassistant.companion.android.compose.HAPreviews
import io.homeassistant.companion.android.onboarding.theme.HATheme

class WelcomeScreenScreenshotTest {
    @HAPreviews
    @Composable
    private fun WelcomeScreenPreview() {
        HATheme {
            WelcomeScreen()
        }
    }
}
