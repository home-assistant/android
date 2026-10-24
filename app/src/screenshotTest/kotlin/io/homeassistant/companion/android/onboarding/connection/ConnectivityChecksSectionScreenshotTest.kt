package io.homeassistant.companion.android.onboarding.connection

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.common.data.connectivity.ConnectivityCheckResult
import io.homeassistant.companion.android.common.data.connectivity.ConnectivityCheckState

class ConnectivityChecksSectionScreenshotTest {

    @PreviewTest
    @Preview
    @Composable
    fun `ConnectivityChecksSection running`() {
        Section(
            ConnectivityCheckState(
                dnsResolution = ConnectivityCheckResult.Success(commonR.string.connection_check_dns, "192.168.0.1"),
                portReachability = ConnectivityCheckResult.InProgress,
            ),
        )
    }

    /** A resolver that never answers: every check needing the hostname is skipped. */
    @PreviewTest
    @Preview
    @Composable
    fun `ConnectivityChecksSection with a DNS timeout`() {
        val skipped = ConnectivityCheckResult.Failure(commonR.string.connection_check_skipped)
        Section(
            ConnectivityCheckState(
                dnsResolution = ConnectivityCheckResult.Failure(commonR.string.connection_check_error_dns_timeout),
                portReachability = skipped,
                tlsCertificate = skipped,
                serverConnection = skipped,
                homeAssistantVerification = skipped,
            ),
        )
    }

    /** A connection too weak to finish what it starts, timing out check after check. */
    @PreviewTest
    @Preview
    @Composable
    fun `ConnectivityChecksSection with a connection timing out`() {
        Section(
            ConnectivityCheckState(
                dnsResolution = ConnectivityCheckResult.Success(commonR.string.connection_check_dns, "192.168.0.1"),
                portReachability = ConnectivityCheckResult.Success(commonR.string.connection_check_port, "443"),
                tlsCertificate = ConnectivityCheckResult.Failure(commonR.string.connection_check_error_tls_timeout),
                serverConnection = ConnectivityCheckResult.Failure(
                    commonR.string.connection_check_error_server_timeout,
                ),
                homeAssistantVerification = ConnectivityCheckResult.Failure(commonR.string.connection_check_skipped),
            ),
        )
    }

    /** The server answered with an error, so the verification it blocks is skipped. */
    @PreviewTest
    @Preview
    @Composable
    fun `ConnectivityChecksSection with server error`() {
        Section(
            ConnectivityCheckState(
                dnsResolution = ConnectivityCheckResult.Success(commonR.string.connection_check_dns, "192.168.0.1"),
                portReachability = ConnectivityCheckResult.Success(commonR.string.connection_check_port, "8123"),
                tlsCertificate = ConnectivityCheckResult.NotApplicable(
                    commonR.string.connection_check_tls_not_applicable,
                ),
                serverConnection = ConnectivityCheckResult.Failure(
                    commonR.string.connection_check_error_http_status,
                    "502",
                ),
                homeAssistantVerification = ConnectivityCheckResult.Failure(commonR.string.connection_check_skipped),
            ),
        )
    }

    /** The server answered, but its manifest is not the one of a Home Assistant instance. */
    @PreviewTest
    @Preview
    @Composable
    fun `ConnectivityChecksSection with an answering server that is not Home Assistant`() {
        Section(
            ConnectivityCheckState(
                dnsResolution = ConnectivityCheckResult.Success(commonR.string.connection_check_dns, "192.168.0.1"),
                portReachability = ConnectivityCheckResult.Success(commonR.string.connection_check_port, "443"),
                tlsCertificate = ConnectivityCheckResult.Success(commonR.string.connection_check_tls_success),
                serverConnection = ConnectivityCheckResult.Success(commonR.string.connection_check_server_success),
                homeAssistantVerification = ConnectivityCheckResult.Failure(
                    commonR.string.connection_check_error_not_home_assistant,
                ),
            ),
        )
    }

    /** A weak connection dropping the manifest download, with the server itself answering fine. */
    @PreviewTest
    @Preview
    @Composable
    fun `ConnectivityChecksSection with a manifest that timed out`() {
        Section(
            ConnectivityCheckState(
                dnsResolution = ConnectivityCheckResult.Success(commonR.string.connection_check_dns, "192.168.0.1"),
                portReachability = ConnectivityCheckResult.Success(commonR.string.connection_check_port, "8123"),
                tlsCertificate = ConnectivityCheckResult.NotApplicable(
                    commonR.string.connection_check_tls_not_applicable,
                ),
                serverConnection = ConnectivityCheckResult.Success(commonR.string.connection_check_server_success),
                homeAssistantVerification = ConnectivityCheckResult.Failure(
                    commonR.string.connection_check_error_server_timeout,
                ),
            ),
        )
    }

    @Composable
    private fun Section(state: ConnectivityCheckState) {
        HAThemeForPreview {
            ConnectivityChecksSection(
                connectivityCheckState = state,
                onRetryConnectivityCheck = {},
            )
        }
    }
}
