package io.homeassistant.companion.android.frontend.gesture

import io.homeassistant.companion.android.frontend.WebViewAction
import io.homeassistant.companion.android.frontend.navigation.FrontendEvent

/**
 * Outcome of a gesture action dispatch.
 *
 * The handler returns this to communicate whether the gesture was acted on or ignored,
 * and whether the ViewModel needs to forward a navigation event or WebView action.
 */
sealed interface GestureResult {
    /** The gesture action was forwarded (e.g., command sent to frontend). */
    data object Forwarded : GestureResult

    /** The gesture was ignored (NONE action, unsupported gesture, or unavailable feature). */
    data object Ignored : GestureResult

    /** The gesture requires navigating to another screen. The ViewModel should emit this event. */
    data class Navigate(val event: FrontendEvent) : GestureResult

    /** The gesture requires a WebView operation. The ViewModel should emit this action. */
    data class PerformWebViewAction(val action: WebViewAction) : GestureResult

    /** The gesture requires switching the active server. The ViewModel should switch to [serverId]. */
    data class SwitchServer(val serverId: Int) : GestureResult

    /**
     * The gesture requires a WebView operation that must complete before continuing.
     *
     * The ViewModel emits [action], awaits its [WebViewAction.AwaitableAction.result],
     * then recursively handles the result of [then].
     */
    data class PerformWebViewActionThen<T>(
        val action: WebViewAction.AwaitableAction<T>,
        val then: suspend () -> GestureResult,
    ) : GestureResult
}
