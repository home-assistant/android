package io.homeassistant.companion.android.onboarding.welcome

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import android.content.res.Configuration.UI_MODE_TYPE_NORMAL
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
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
                onRejectClick = {},
                onLearnMoreClick = {},
            )
        }
    }

    @PreviewTest
    @Preview
    @Preview(
        name = "tablet",
        device = "spec:width=1280dp,height=800dp,dpi=320,orientation=portrait",
        group = "tablet",
        uiMode = UI_MODE_NIGHT_YES or UI_MODE_TYPE_NORMAL,
    )
    @Composable
    fun `WelcomeInvitationScreen with long URL`() {
        HAThemeForPreview {
            WelcomeInvitationScreen(
                serverUrl =
                "http://homeassistant.local:8123/long/url/to/see/how/it/breaks/the/ui/in/the/screen/a/bit/longer/just/to/be/sure",
                onAcceptClick = {},
                onRejectClick = {},
                onLearnMoreClick = {},
            )
        }
    }
}
