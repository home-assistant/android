package io.homeassistant.companion.android.frontend.error

import io.homeassistant.companion.android.common.R as commonR
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ErrorActionsTest {

    private fun actionsFor(error: FrontendConnectionError, isInternalConnection: Boolean = true) = errorActions(error, isInternalConnection)

    private val tlsNotFound = FrontendConnectionError.TlsCertNotFound(null, "t")
    private val tlsExpired = FrontendConnectionError.TlsCertExpired(null, "t")
    private val authRevoked = FrontendConnectionError.AuthRevoked(commonR.string.error_auth_revoked, null, "t")
    private val ssl = FrontendConnectionError.SslError(commonR.string.webview_error_SSL_UNTRUSTED, null, "t")
    private val externalBusTimeout = FrontendConnectionError.ExternalBusTimeout
    private val timeout = FrontendConnectionError.Timeout(null, "t")
    private val unreachable = FrontendConnectionError.Unreachable(commonR.string.webview_error_HOST_LOOKUP, null, "t")
    private val unknown = FrontendConnectionError.Unknown(null, "t")
    private val webViewCreation = FrontendConnectionError.Unrecoverable.WebViewCreationError(RuntimeException("boom"))

    @Test
    fun `Given TlsCertNotFound when building actions then remove-server primary then settings`() {
        assertEquals(
            listOf(
                ErrorAction(commonR.string.error_action_remove_server, ErrorAction.Style.Primary, ErrorActionIntent.RemoveServerAndRelaunch),
                ErrorAction(commonR.string.open_settings, ErrorAction.Style.Secondary, ErrorActionIntent.GoToSettings),
            ),
            actionsFor(tlsNotFound),
        )
    }

    @Test
    fun `Given AuthRevoked when building actions then remove-server primary then settings`() {
        assertEquals(
            listOf(ErrorActionIntent.RemoveServerAndRelaunch, ErrorActionIntent.GoToSettings),
            actionsFor(authRevoked).map { it.intent },
        )
    }

    @Test
    fun `Given TlsCertExpired when building actions then clear-credentials primary then settings`() {
        assertEquals(
            listOf(
                ErrorAction(commonR.string.error_action_clear_credentials, ErrorAction.Style.Primary, ErrorActionIntent.ClearKeychainAndRelaunch),
                ErrorAction(commonR.string.open_settings, ErrorAction.Style.Secondary, ErrorActionIntent.GoToSettings),
            ),
            actionsFor(tlsExpired),
        )
    }

    @Test
    fun `Given SslError when building actions then only settings as primary`() {
        assertEquals(
            listOf(ErrorAction(commonR.string.open_settings, ErrorAction.Style.Primary, ErrorActionIntent.GoToSettings)),
            actionsFor(ssl),
        )
    }

    @Test
    fun `Given WebViewCreationError when building actions then only settings as primary`() {
        assertEquals(
            listOf(ErrorAction(commonR.string.open_settings, ErrorAction.Style.Primary, ErrorActionIntent.GoToSettings)),
            actionsFor(webViewCreation),
        )
    }

    @Test
    fun `Given ExternalBusTimeout when building actions then refresh settings and wait`() {
        assertEquals(
            listOf(
                ErrorActionIntent.Refresh,
                ErrorActionIntent.GoToSettings,
                ErrorActionIntent.Wait,
            ),
            actionsFor(externalBusTimeout, isInternalConnection = false).map { it.intent },
        )
    }

    @Test
    fun `Given general Timeout when building actions then refresh and settings without wait`() {
        assertEquals(
            listOf(ErrorActionIntent.Refresh, ErrorActionIntent.GoToSettings),
            actionsFor(timeout, isInternalConnection = true).map { it.intent },
        )
    }

    @Test
    fun `Given Unreachable and Unknown when building actions then refresh and settings`() {
        listOf(unreachable, unknown).forEach { error ->
            assertEquals(
                listOf(ErrorActionIntent.Refresh, ErrorActionIntent.GoToSettings),
                actionsFor(error, isInternalConnection = false).map { it.intent },
                "actions for $error",
            )
        }
    }

    @Test
    fun `Given internal connection when refreshing then label is refresh internal`() {
        assertEquals(commonR.string.refresh_internal, actionsFor(timeout, isInternalConnection = true).first().labelRes)
    }

    @Test
    fun `Given external connection when refreshing then label is refresh external`() {
        assertEquals(commonR.string.refresh_external, actionsFor(timeout, isInternalConnection = false).first().labelRes)
    }
}
