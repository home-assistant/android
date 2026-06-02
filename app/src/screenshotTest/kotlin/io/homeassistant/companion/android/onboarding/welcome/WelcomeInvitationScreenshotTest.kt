package io.homeassistant.companion.android.onboarding.welcome

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.util.compose.HAPreviews

class WelcomeInvitationScreenshotTest {

    @PreviewTest
    @HAPreviews
    @Composable
    fun `WelcomeInvitationScreen`() {
        HAThemeForPreview {
            WelcomeInvitationScreen(
                serverUrl = "http://homeassistant.local:8123",
                onAcceptClick = {},
                onLearnMoreClick = {},
            )
        }
    }
}
