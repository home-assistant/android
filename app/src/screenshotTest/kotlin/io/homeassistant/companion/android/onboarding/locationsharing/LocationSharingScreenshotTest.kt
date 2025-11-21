package io.homeassistant.companion.android.onboarding.locationsharing

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.compose.HAPreviews

class LocationSharingScreenshotTest {

    @PreviewTest
    @HAPreviews
    @Composable
    fun `LocationSharing empty`() {
        HAThemeForPreview {
            LocationSharingScreen(
                onHelpClick = {},
                onGoToNextScreen = {},
                onLocationSharingResponse = {},
            )
        }
    }
}
