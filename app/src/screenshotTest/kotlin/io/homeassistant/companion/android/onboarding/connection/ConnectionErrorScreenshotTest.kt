package io.homeassistant.companion.android.onboarding.connection

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.common.data.connectivity.ConnectivityCheckResult
import io.homeassistant.companion.android.common.data.connectivity.ConnectivityCheckState
import io.homeassistant.companion.android.frontend.error.FrontendError
import io.homeassistant.companion.android.frontend.error.FrontendErrorStateProvider
import io.homeassistant.companion.android.util.compose.HAPreviews
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class ConnectionErrorScreenshotTest {

    @PreviewTest
    @HAPreviews
    @Composable
    fun `ConnectionErrorScreen no error`() {
        HAThemeForPreview {
            ConnectionErrorScreen(
                stateProvider = FakeErrorStateProvider(url = null, error = null),
                onOpenExternalLink = {},
                onCloseClick = {},
            )
        }
    }

    @PreviewTest
    @HAPreviews
    @Composable
    fun `ConnectionErrorScreen with error expanded`() {
        HAThemeForPreview {
            ConnectionErrorScreen(
                stateProvider = FakeErrorStateProvider(
                    url = null,
                    error = FrontendError.AuthenticationError(
                        commonR.string.tls_cert_expired_message,
                        "Error code: 403, Description: forbidden",
                        "raw",
                    ),
                    connectivityState = ConnectivityCheckState(
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
                ),
                onOpenExternalLink = {},
                onCloseClick = {},
            )
        }
    }

    @PreviewTest
    @HAPreviews
    @Composable
    fun `ConnectionErrorScreen with AuthenticationError`() {
        HAThemeForPreview {
            ConnectionErrorScreen(
                stateProvider = FakeErrorStateProvider(
                    url = null,
                    error = FrontendError.AuthenticationError(
                        commonR.string.tls_cert_expired_message,
                        "details",
                        "raw",
                    ),
                ),
                onOpenExternalLink = {},
                onCloseClick = {},
            )
        }
    }

    @PreviewTest
    @HAPreviews
    @Composable
    fun `ConnectionErrorScreen with UnreachableError`() {
        HAThemeForPreview {
            ConnectionErrorScreen(
                stateProvider = FakeErrorStateProvider(
                    url = "http://ha.org",
                    error = FrontendError.UnreachableError(
                        commonR.string.tls_cert_expired_message,
                        "details",
                        "raw",
                    ),
                ),
                onOpenExternalLink = {},
                onCloseClick = {},
            )
        }
    }

    @PreviewTest
    @HAPreviews
    @Composable
    fun `ConnectionErrorScreen with UnknownError`() {
        HAThemeForPreview {
            ConnectionErrorScreen(
                stateProvider = FakeErrorStateProvider(
                    url =
                    "http://super-long-url-to-see-how-it-displays-in-the-screenshot.org/path/1/home-assistant/io?external_auth=1",
                    error = FrontendError.UnknownError(
                        commonR.string.tls_cert_expired_message,
                        "details",
                        "raw",
                    ),
                ),
                onOpenExternalLink = {},
                onCloseClick = {},
            )
        }
    }
}

private class FakeErrorStateProvider(
    url: String?,
    error: FrontendError?,
    connectivityState: ConnectivityCheckState = ConnectivityCheckState(),
) : FrontendErrorStateProvider {
    override val urlFlow: StateFlow<String?> = MutableStateFlow(url)
    override val errorFlow: StateFlow<FrontendError?> = MutableStateFlow(error)
    override val connectivityCheckState: StateFlow<ConnectivityCheckState> = MutableStateFlow(connectivityState)
    override fun runConnectivityChecks() = Unit
}
