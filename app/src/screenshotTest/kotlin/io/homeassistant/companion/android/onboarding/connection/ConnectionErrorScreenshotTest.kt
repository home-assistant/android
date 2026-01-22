package io.homeassistant.companion.android.onboarding.connection

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.android.tools.screenshot.PreviewTest
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.common.data.connectivity.ConnectivityCheckResult
import io.homeassistant.companion.android.common.data.connectivity.ConnectivityCheckState
import io.homeassistant.companion.android.util.compose.HAPreviews

class ConnectionErrorScreenshotTest {

    @PreviewTest
    @HAPreviews
    @Composable
    fun `ConnectionErrorScreen no error`() {
        HAThemeForPreview {
            ConnectionErrorScreen(
                url = null,
                error = null,
                onOpenExternalLink = {},
                onCloseClick = {},
                connectivityCheckState = ConnectivityCheckState(),
                onRetryConnectivityCheck = {},
            )
        }
    }

    @PreviewTest
    @HAPreviews
    @Composable
    fun `ConnectionErrorScreen with error expanded`() {
        HAThemeForPreview {
            ConnectionErrorScreen(
                url = null,
                error = ConnectionError.AuthenticationError(
                    commonR.string.tls_cert_expired_message,
                    stringResource(commonR.string.connection_error_more_details_description_content, 403, "forbidden"),
                    "raw",
                ),
                onOpenExternalLink = {},
                onCloseClick = {},
                connectivityCheckState = ConnectivityCheckState(
                    dnsResolution = ConnectivityCheckResult.Success(commonR.string.connection_check_dns, "192.168.0.1"),
                    portReachability = ConnectivityCheckResult.Success(commonR.string.connection_check_port, "8123"),
                    tlsCertificate = ConnectivityCheckResult.Failure(commonR.string.connection_check_error_tls),
                    serverConnection = ConnectivityCheckResult.Pending,
                    homeAssistantVerification = ConnectivityCheckResult.Pending,
                ),
                onRetryConnectivityCheck = {},
                errorDetailsExpanded = true,
            )
        }
    }

    @PreviewTest
    @HAPreviews
    @Composable
    fun `ConnectionErrorScreen with AuthenticationError`() {
        HAThemeForPreview {
            ConnectionErrorScreen(
                url = null,
                error = ConnectionError.AuthenticationError(commonR.string.tls_cert_expired_message, "details", "raw"),
                onOpenExternalLink = {},
                onCloseClick = {},
                connectivityCheckState = ConnectivityCheckState(),
                onRetryConnectivityCheck = {},
            )
        }
    }

    @PreviewTest
    @HAPreviews
    @Composable
    fun `ConnectionErrorScreen with UnreachableError`() {
        HAThemeForPreview {
            ConnectionErrorScreen(
                url = "http://ha.org",
                error = ConnectionError.UnreachableError(commonR.string.tls_cert_expired_message, "details", "raw"),
                onOpenExternalLink = {},
                onCloseClick = {},
                connectivityCheckState = ConnectivityCheckState(),
                onRetryConnectivityCheck = {},
            )
        }
    }

    @PreviewTest
    @HAPreviews
    @Composable
    fun `ConnectionErrorScreen with UnknownError`() {
        HAThemeForPreview {
            ConnectionErrorScreen(
                url =
                "http://super-long-url-to-see-how-it-displays-in-the-screenshot.org/path/1/home-assistant/io?external_auth=1",
                error = ConnectionError.UnknownError(commonR.string.tls_cert_expired_message, "details", "raw"),
                onOpenExternalLink = {},
                onCloseClick = {},
                connectivityCheckState = ConnectivityCheckState(),
                onRetryConnectivityCheck = {},
            )
        }
    }
}
