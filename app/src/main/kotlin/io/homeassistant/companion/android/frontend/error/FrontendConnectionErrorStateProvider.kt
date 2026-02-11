package io.homeassistant.companion.android.frontend.error

import io.homeassistant.companion.android.common.data.connectivity.ConnectivityCheckState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Interface that provide connection error state.
 *
 * Implement this interface to enable use of the shared [FrontendConnectionErrorScreen].
 */
interface FrontendConnectionErrorStateProvider {
    /** The URL being connected to, or null if not yet determined */
    val urlFlow: StateFlow<String?>

    /** The current connection error, or null if no error */
    val errorFlow: StateFlow<FrontendConnectionError?>

    /** The current connectivity check state */
    val connectivityCheckState: StateFlow<ConnectivityCheckState>

    /** Run connectivity checks against the server URL */
    fun runConnectivityChecks()

    companion object {
        /** A no-op implementation for use in tests and previews. */
        val noOp = object : FrontendConnectionErrorStateProvider {
            override val urlFlow: StateFlow<String?> = MutableStateFlow(null)
            override val errorFlow: StateFlow<FrontendConnectionError?> = MutableStateFlow(null)
            override val connectivityCheckState: StateFlow<ConnectivityCheckState> =
                MutableStateFlow(ConnectivityCheckState())
            override fun runConnectivityChecks() = Unit
        }
    }
}
