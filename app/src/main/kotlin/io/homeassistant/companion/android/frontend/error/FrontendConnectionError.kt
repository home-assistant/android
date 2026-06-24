package io.homeassistant.companion.android.frontend.error

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.R as commonR

/**
 * Represents errors that can occur when connecting to a Home Assistant server.
 *
 * This sealed interface provides a type-safe way to handle different connection
 * error scenarios with appropriate user-facing messages. The concrete subtype determines
 * the [icon] shown and which recovery actions the error screen offers (see `ErrorActionsProvider`).
 */
sealed interface FrontendConnectionError {
    @get:StringRes
    val title: Int

    @get:StringRes
    val message: Int

    @get:DrawableRes
    val icon: Int

    val errorDetails: String?

    val rawErrorType: String

    /**
     * The server is unreachable: DNS/host lookup failure, connection refused, or no URL available.
     */
    data class Unreachable(
        @StringRes override val message: Int,
        override val errorDetails: String?,
        override val rawErrorType: String,
    ) : FrontendConnectionError {
        override val title: Int = commonR.string.error_connection_failed
        override val icon: Int = R.drawable.ic_casita_no_connection
    }

    /**
     * A WebView-level timeout while connecting to the server.
     */
    data class Timeout(
        @StringRes override val message: Int,
        override val errorDetails: String?,
        override val rawErrorType: String,
    ) : FrontendConnectionError {
        override val title: Int = commonR.string.error_connection_failed
        override val icon: Int = R.drawable.ic_casita_no_connection
    }

    /**
     * The page loaded but the frontend never completed its external-bus handshake within the
     * connection timeout. Distinct from [Timeout] because the recovery offers an extra "Wait"
     * action to keep waiting for the handshake.
     */
    data class ExternalBusTimeout(
        @StringRes override val message: Int,
        override val errorDetails: String?,
        override val rawErrorType: String,
    ) : FrontendConnectionError {
        override val title: Int = commonR.string.error_connection_failed
        override val icon: Int = R.drawable.ic_casita_no_connection
    }

    /**
     * Authentication/session is no longer valid (revoked token, anonymous session, auth scheme
     * failures). Recovery removes the server and relaunches.
     */
    data class AuthRevoked(
        @StringRes override val message: Int,
        override val errorDetails: String?,
        override val rawErrorType: String,
    ) : FrontendConnectionError {
        override val title: Int = commonR.string.error_connection_failed
        override val icon: Int = R.drawable.ic_casita_crying
    }

    /**
     * A server-certificate (TLS/SSL) validation error, e.g. expired or untrusted server certificate.
     */
    data class SslError(
        @StringRes override val message: Int,
        override val errorDetails: String?,
        override val rawErrorType: String,
    ) : FrontendConnectionError {
        override val title: Int = commonR.string.error_connection_failed
        override val icon: Int = R.drawable.ic_casita_crying
    }

    /**
     * The server requires a TLS client certificate but none is available (key denied / HTTP 400).
     * Recovery removes the server and relaunches.
     */
    data class TlsCertNotFound(
        @StringRes override val message: Int,
        override val errorDetails: String?,
        override val rawErrorType: String,
    ) : FrontendConnectionError {
        override val title: Int = commonR.string.tls_cert_title
        override val icon: Int = R.drawable.ic_casita_crying
    }

    /**
     * The installed TLS client certificate chain is no longer valid. Recovery clears the keychain
     * and relaunches.
     */
    data class TlsCertExpired(
        @StringRes override val message: Int,
        override val errorDetails: String?,
        override val rawErrorType: String,
    ) : FrontendConnectionError {
        override val title: Int = commonR.string.tls_cert_title
        override val icon: Int = R.drawable.ic_casita_crying
    }

    /**
     * Errors that are not recoverable by retrying the connection, such as
     * WebView creation failures due to system-level issues.
     * No retry attempts should be made for these errors.
     */
    sealed interface Unrecoverable : FrontendConnectionError {
        /**
         * WebView creation failure due to a system-level issue such as a broken,
         * missing, or ABI-incompatible WebView provider.
         *
         * Unlike other errors, this is not a connection issue but a device configuration
         * problem. The recommended user action is to check and update the system WebView
         * via device settings.
         */
        data class WebViewCreationError(@StringRes override val message: Int, val throwable: Throwable) :
            Unrecoverable {
            override val title: Int = commonR.string.webview_creation_error_title
            override val icon: Int = R.drawable.ic_casita_problem
            override val errorDetails: String = throwable.message ?: throwable.toString()
            override val rawErrorType: String = throwable::class.toString()
        }
    }

    /**
     * Unknown or unexpected errors that don't fit other categories.
     */
    data class Unknown(
        @StringRes override val message: Int,
        override val errorDetails: String?,
        override val rawErrorType: String,
    ) : FrontendConnectionError {
        override val title: Int = commonR.string.error_connection_failed
        override val icon: Int = R.drawable.ic_casita_problem
    }
}
