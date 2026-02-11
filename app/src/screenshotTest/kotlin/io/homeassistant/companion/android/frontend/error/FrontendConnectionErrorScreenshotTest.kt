package io.homeassistant.companion.android.frontend.error

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.common.data.connectivity.ConnectivityCheckResult
import io.homeassistant.companion.android.common.data.connectivity.ConnectivityCheckState
import io.homeassistant.companion.android.util.compose.HAPreviews

class FrontendConnectionErrorScreenshotTest {

    @PreviewTest
    @HAPreviews
    @Composable
    fun `FrontendConnectionErrorScreen no error`() {
        HAThemeForPreview {
            FrontendConnectionErrorScreen(
                url = null,
                error = null,
                onOpenExternalLink = {},
                connectivityCheckState = ConnectivityCheckState(),
                actions = {},
            )
        }
    }

    @PreviewTest
    @HAPreviews
    @Composable
    fun `FrontendConnectionErrorScreen with error expanded`() {
        HAThemeForPreview {
            FrontendConnectionErrorScreen(
                url = null,
                error = FrontendConnectionError.AuthenticationError(
                    commonR.string.tls_cert_expired_message,
                    "Error code: 403, Description: forbidden",
                    "raw",
                ),
                connectivityCheckState = ConnectivityCheckState(
                    dnsResolution = ConnectivityCheckResult.Success(
                        commonR.string.connection_check_dns,
                        "192.168.0.1",
                    ),
                    portReachability = ConnectivityCheckResult.Success(
                        commonR.string.connection_check_port,
                        "8123",
                    ),
                    tlsCertificate = ConnectivityCheckResult.Failure(commonR.string.connection_check_error_tls),
                    serverConnection = ConnectivityCheckResult.Pending,
                    homeAssistantVerification = ConnectivityCheckResult.Pending,
                ),
                onOpenExternalLink = {},
                actions = {},
                errorDetailsExpanded = true,
            )
        }
    }

    @PreviewTest
    @HAPreviews
    @Composable
    fun `FrontendConnectionErrorScreen with AuthenticationError`() {
        HAThemeForPreview {
            FrontendConnectionErrorScreen(
                url = null,
                error = FrontendConnectionError.AuthenticationError(
                    commonR.string.tls_cert_expired_message,
                    "details",
                    "raw",
                ),
                onOpenExternalLink = {},
                connectivityCheckState = ConnectivityCheckState(),
                actions = {},
            )
        }
    }

    @PreviewTest
    @HAPreviews
    @Composable
    fun `FrontendConnectionErrorScreen with UnreachableError`() {
        HAThemeForPreview {
            FrontendConnectionErrorScreen(
                url = "http://ha.org",
                error = FrontendConnectionError.UnreachableError(
                    commonR.string.tls_cert_expired_message,
                    "details",
                    "raw",
                ),
                onOpenExternalLink = {},
                connectivityCheckState = ConnectivityCheckState(),
                actions = {},
            )
        }
    }

    @PreviewTest
    @HAPreviews
    @Composable
    fun `FrontendConnectionErrorScreen with UnknownError`() {
        HAThemeForPreview {
            FrontendConnectionErrorScreen(
                url =
                "http://super-long-url-to-see-how-it-displays-in-the-screenshot.org/path/1/home-assistant/io?external_auth=1",
                error = FrontendConnectionError.UnknownError(
                    commonR.string.tls_cert_expired_message,
                    "details",
                    "raw",
                ),
                onOpenExternalLink = {},
                connectivityCheckState = ConnectivityCheckState(),
                actions = {},
            )
        }
    }
}
