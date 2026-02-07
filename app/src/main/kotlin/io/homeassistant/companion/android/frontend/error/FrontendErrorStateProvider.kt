package io.homeassistant.companion.android.frontend.error

import io.homeassistant.companion.android.common.data.connectivity.ConnectivityCheckState
import kotlinx.coroutines.flow.StateFlow

/**
 * Interface that provide connection error state.
 *
 * Implement this interface to enable use of the shared [FrontendErrorScreen].
 */
interface FrontendErrorStateProvider {
    /** The URL being connected to, or null if not yet determined */
    val urlFlow: StateFlow<String?>

    /** The current connection error, or null if no error */
    val errorFlow: StateFlow<FrontendError?>

    /** The current connectivity check state */
    val connectivityCheckState: StateFlow<ConnectivityCheckState>

    /** Run connectivity checks against the server URL */
    fun runConnectivityChecks()
}
