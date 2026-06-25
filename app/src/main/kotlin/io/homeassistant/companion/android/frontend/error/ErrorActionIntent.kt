package io.homeassistant.companion.android.frontend.error

/**
 * An action a user can take from the connection-error screen.
 *
 * Produced by [errorActions] and handled by the frontend ViewModel / host. Kept free of any
 * UI concern (label, styling) so the same intent can be rendered differently per error.
 */
sealed interface ErrorActionIntent {
    /** Remove the current server and relaunch the app from scratch. */
    data object RemoveServerAndRelaunch : ErrorActionIntent

    /** Clear the stored TLS client credentials (keychain) and relaunch the app from scratch. */
    data object ClearKeychainAndRelaunch : ErrorActionIntent

    /** Reload the server connection. */
    data object Refresh : ErrorActionIntent

    /** Dismiss the error and keep waiting for the frontend's external-bus handshake. */
    data object Wait : ErrorActionIntent

    /** Open the app settings. */
    data object GoToSettings : ErrorActionIntent
}
