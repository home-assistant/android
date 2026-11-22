package io.homeassistant.companion.android.frontend.error

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.common.data.connectivity.ConnectivityCheckResult
import io.homeassistant.companion.android.common.data.connectivity.ConnectivityCheckState
import io.homeassistant.companion.android.util.compose.HAPreviews
import io.homeassistant.companion.android.util.compose.webview.BLANK_URL

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
                url = "http://home-assistant.local:8123",
                error = FrontendConnectionError.AuthRevoked(
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
                url = "http://home-assistant.local:8123",
                error = FrontendConnectionError.AuthRevoked(
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
                error = FrontendConnectionError.Unreachable(
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
                error = FrontendConnectionError.Unknown(
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
    fun `FrontendConnectionErrorScreen with null url expanded`() {
        HAThemeForPreview {
            FrontendConnectionErrorScreen(
                url = null,
                error = FrontendConnectionError.Unknown(
                    "details",
                    "raw",
                ),
                onOpenExternalLink = {},
                connectivityCheckState = ConnectivityCheckState(),
                actions = {},
                errorDetailsExpanded = true,
            )
        }
    }

    @PreviewTest
    @HAPreviews
    @Composable
    fun `FrontendConnectionErrorScreen with WebViewCreationError and blank URL`() {
        HAThemeForPreview {
            FrontendConnectionErrorScreen(
                url = BLANK_URL,
                error = FrontendConnectionError.Unrecoverable.WebViewCreationError(
                    throwable = UnsatisfiedLinkError(
                        "dlopen failed: libwebviewchromium.so is 32-bit instead of 64-bit",
                    ),
                ),
                onOpenExternalLink = {},
                connectivityCheckState = ConnectivityCheckState(),
                actions = {},
            )
        }
    }

    // One screenshot per error subtype, each rendering the recovery actions the screen offers for
    // that error (see errorActions). Uses a single @Preview (not @HAPreviews).

    @PreviewTest
    @Preview
    @Composable
    fun `FrontendConnectionErrorScreen Unreachable actions`() = ErrorActionsPreview(
        FrontendConnectionError.Unreachable(
            commonR.string.webview_error_HOST_LOOKUP,
            "net::ERR_NAME_NOT_RESOLVED",
            "raw",
        ),
    )

    @PreviewTest
    @Preview
    @Composable
    fun `FrontendConnectionErrorScreen Timeout actions`() = ErrorActionsPreview(
        FrontendConnectionError.Timeout("net::ERR_TIMED_OUT", "raw"),
    )

    @PreviewTest
    @Preview
    @Composable
    fun `FrontendConnectionErrorScreen ExternalBusTimeout actions`() = ErrorActionsPreview(
        FrontendConnectionError.ExternalBusTimeout,
    )

    @PreviewTest
    @Preview
    @Composable
    fun `FrontendConnectionErrorScreen AuthRevoked actions`() = ErrorActionsPreview(
        FrontendConnectionError.AuthRevoked(commonR.string.error_auth_revoked, "Error code: 401", "raw"),
    )

    @PreviewTest
    @Preview
    @Composable
    fun `FrontendConnectionErrorScreen SslError actions`() = ErrorActionsPreview(
        FrontendConnectionError.SslError(commonR.string.webview_error_SSL_UNTRUSTED, "SSL_UNTRUSTED", "raw"),
    )

    @PreviewTest
    @Preview
    @Composable
    fun `FrontendConnectionErrorScreen TlsCertNotFound actions`() = ErrorActionsPreview(
        FrontendConnectionError.TlsCertNotFound("HTTP 400", "raw"),
    )

    @PreviewTest
    @Preview
    @Composable
    fun `FrontendConnectionErrorScreen TlsCertExpired actions`() = ErrorActionsPreview(
        FrontendConnectionError.TlsCertExpired("certificate chain invalid", "raw"),
    )

    @PreviewTest
    @Preview
    @Composable
    fun `FrontendConnectionErrorScreen Unknown actions`() = ErrorActionsPreview(
        FrontendConnectionError.Unknown("unexpected", "raw"),
    )

    @PreviewTest
    @Preview
    @Composable
    fun `FrontendConnectionErrorScreen WebViewCreationError actions`() = ErrorActionsPreview(
        FrontendConnectionError.Unrecoverable.WebViewCreationError(
            UnsatisfiedLinkError("dlopen failed: libwebviewchromium.so is 32-bit instead of 64-bit"),
        ),
    )

    @Composable
    private fun ErrorActionsPreview(
        error: FrontendConnectionError,
        url: String? = "http://home-assistant.local:8123",
    ) {
        HAThemeForPreview {
            FrontendConnectionErrorScreen(
                url = url,
                error = error,
                onOpenExternalLink = {},
                connectivityCheckState = ConnectivityCheckState(),
                actions = {
                    ErrorActions(
                        actions = errorActions(error, isInternalConnection = false),
                        onAction = {},
                    )
                },
            )
        }
    }
}
