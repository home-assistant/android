package io.homeassistant.companion.android.frontend.error

import androidx.annotation.StringRes
import io.homeassistant.companion.android.common.R as commonR

/**
 * Represents errors that can occur when connecting to a Home Assistant server.
 *
 * This sealed interface provides a type-safe way to handle different connection
 * error scenarios with appropriate user-facing messages.
 */
sealed interface FrontendError {
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
    ) : FrontendError {
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
    ) : FrontendError {
        override val title: Int = commonR.string.error_connection_failed
    }

    /**
     * Unknown or unexpected errors that don't fit other categories.
     */
    data class UnknownError(
        @StringRes override val message: Int,
        override val errorDetails: String,
        override val rawErrorType: String,
    ) : FrontendError {
        override val title: Int = commonR.string.error_connection_failed
    }
}
