package io.homeassistant.companion.android.webview.error

import androidx.annotation.StringRes
import io.homeassistant.companion.android.common.R as commonR

/**
 * Represents errors that can occur when connecting to a Home Assistant server.
 *
 * This sealed interface provides a type-safe way to handle different connection
 * error scenarios with appropriate user-facing messages.
 */
sealed interface ConnectionError {
    /** String resource ID for the error title */
    @get:StringRes
    val title: Int

    /** String resource ID for the error message */
    @get:StringRes
    val message: Int

    /** Optional detailed error description for debugging */
    val errorDetails: String?

    /** The raw error type for logging and debugging purposes */
    val rawErrorType: String

    /**
     * Authentication-related errors such as SSL handshake failures,
     * invalid certificates, or proxy authentication issues.
     */
    data class AuthenticationError(
        @StringRes override val message: Int,
        override val errorDetails: String,
        override val rawErrorType: String,
    ) : ConnectionError {
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
    ) : ConnectionError {
        override val title: Int = commonR.string.error_connection_failed
    }

    /**
     * Unknown or unexpected errors that don't fit other categories.
     */
    data class UnknownError(
        @StringRes override val message: Int,
        override val errorDetails: String,
        override val rawErrorType: String,
    ) : ConnectionError {
        override val title: Int = commonR.string.error_connection_failed
    }
}
