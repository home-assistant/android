package io.homeassistant.companion.android.onboarding.locationforsecureconnection

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.compose.HAPreviews

class LocationForSecureConnectionScreenshotTest {
    @PreviewTest
    @HAPreviews
    @Composable
    fun `LocationForSecureConnection empty`() {
        HAThemeForPreview {
            LocationForSecureConnectionScreen(
                onAllowInsecureConnection = {},
                onGoToNextScreen = {},
                onHelpClick = {},
                onShowSnackbar = { _, _ -> true },
            )
        }
    }
}
