package io.homeassistant.companion.android.frontend.error

import kotlinx.coroutines.flow.StateFlow

/**
 * Interface for ViewModels that provide connection error state.
 *
 * Implement this interface in ViewModels that handle WebView connections
 * to enable use of the shared [FrontendErrorScreen].
 */
interface FrontendErrorStateProvider {
    /** The URL being connected to, or null if not yet determined */
    val urlFlow: StateFlow<String?>

    /** The current connection error, or null if no error */
    val errorFlow: StateFlow<FrontendError?>
}
