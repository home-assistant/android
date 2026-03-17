package io.homeassistant.companion.android.frontend.error

import androidx.annotation.StringRes
import io.homeassistant.companion.android.common.R as commonR

/**
 * Represents errors that can occur when connecting to a Home Assistant server.
 *
 * This sealed interface provides a type-safe way to handle different connection
 * error scenarios with appropriate user-facing messages.
 */
sealed interface FrontendConnectionError {
    @get:StringRes
    val title: Int

    @get:StringRes
    val message: Int

    val errorDetails: String?

    val rawErrorType: String

    /**
     * Authentication-related errors such as SSL handshake failures,
     * invalid certificates, or proxy authentication issues.
     */
    data class AuthenticationError(
        @StringRes override val message: Int,
        override val errorDetails: String,
        override val rawErrorType: String,
    ) : FrontendConnectionError {
        override val title: Int = commonR.string.error_connection_failed
    }

    /**
     * Server unreachable errors such as DNS lookup failures
     * or network connectivity issues.
     */
    data class UnreachableError(
        @StringRes override val message: Int,
        override val errorDetails: String?,
        override val rawErrorType: String,
    ) : FrontendConnectionError {
        override val title: Int = commonR.string.error_connection_failed
    }

    /**
     * Errors that are not recoverable by retrying the connection, such as
     * WebView creation failures due to system-level issues.
     * No retry attempts should be made for these errors.
     */
    sealed interface UnrecoverableError : FrontendConnectionError {
        /**
         * WebView creation failure due to a system-level issue such as a broken,
         * missing, or ABI-incompatible WebView provider.
         *
         * Unlike other errors, this is not a connection issue but a device configuration
         * problem. The recommended user action is to check and update the system WebView
         * via device settings.
         */
        data class WebViewCreationError(
            @StringRes override val message: Int,
            val throwable: Throwable,
        ) : UnrecoverableError {
            override val title: Int = commonR.string.webview_creation_error_title
            override val errorDetails: String = throwable.message ?: throwable.toString()
            override val rawErrorType: String = throwable::class.toString()
        }
    }

    /**
     * Unknown or unexpected errors that don't fit other categories.
     */
    data class UnknownError(
        @StringRes override val message: Int,
        override val errorDetails: String,
        override val rawErrorType: String,
    ) : FrontendConnectionError {
        override val title: Int = commonR.string.error_connection_failed
    }
}
