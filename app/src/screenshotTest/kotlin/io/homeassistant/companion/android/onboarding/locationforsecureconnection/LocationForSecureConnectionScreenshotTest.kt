package io.homeassistant.companion.android.onboarding.locationforsecureconnection

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.util.compose.HAPreviews

class LocationForSecureConnectionScreenshotTest {
    @PreviewTest
    @HAPreviews
    @Composable
    fun `LocationForSecureConnection empty`() {
        HAThemeForPreview {
            LocationForSecureConnectionScreen(
                onAllowInsecureConnection = { _ -> },
                onHelpClick = {},
                onShowSnackbar = { _, _ -> true },
                initialAllowInsecureConnection = null,
            )
        }
    }

    @PreviewTest
    @HAPreviews
    @Composable
    fun `LocationForSecureConnection with back navigation`() {
        HAThemeForPreview {
            LocationForSecureConnectionScreen(
                onAllowInsecureConnection = { _ -> },
                onHelpClick = {},
                onBackClick = {},
                onShowSnackbar = { _, _ -> true },
                initialAllowInsecureConnection = null,
            )
        }
    }

    @PreviewTest
    @HAPreviews
    @Composable
    fun `LocationForSecureConnection with close navigation`() {
        HAThemeForPreview {
            LocationForSecureConnectionScreen(
                onAllowInsecureConnection = { _ -> },
                onHelpClick = {},
                onCloseClick = {},
                onShowSnackbar = { _, _ -> true },
                initialAllowInsecureConnection = null,
            )
        }
    }

    @PreviewTest
    @HAPreviews
    @Composable
    fun `LocationForSecureConnection most secure selected`() {
        HAThemeForPreview {
            LocationForSecureConnectionScreen(
                onAllowInsecureConnection = { _ -> },
                onHelpClick = {},
                onShowSnackbar = { _, _ -> true },
                initialAllowInsecureConnection = false,
            )
        }
    }
}
