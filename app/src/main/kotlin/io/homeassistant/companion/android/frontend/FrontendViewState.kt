package io.homeassistant.companion.android.frontend

import androidx.compose.ui.graphics.Color
import io.homeassistant.companion.android.common.data.prefs.NightModeTheme
import io.homeassistant.companion.android.frontend.error.FrontendConnectionError
import org.jetbrains.annotations.VisibleForTesting

@VisibleForTesting
const val BLANK_URL = "about:blank"

/**
 * Represents the persistent UI state of the frontend screen.
 *
 * Use this for states that determine what UI is rendered and should persist until explicitly
 * changed (e.g., loading indicators, error screens, security prompts). The state survives
 * recomposition and is preserved until a user action or system event triggers a transition.
 *
 * For one-shot navigation to external screens (e.g., opening Settings), use
 * [io.homeassistant.companion.android.frontend.navigation.FrontendNavigationEvent] instead.
 * Navigation events are consumed once and don't persist in state.
 */
sealed interface FrontendViewState {

    /** The server ID associated with this state */
    val serverId: Int

    /** The URL being loaded or displayed */
    val url: String

    /**
     * Initial state before the URL is determined.
     *
     * This state is shown while resolving the server URL.
     * The timeout timer does not start until transitioning to [Loading].
     *
     * The [url] is set to about:blank to clear the webview of any previously loaded URL
     */
    data class LoadServer(override val serverId: Int, val path: String? = null) : FrontendViewState {
        override val url: String = BLANK_URL
    }

    /**
     * Loading state while the WebView is loading the URL.
     *
     * The connection timeout starts when entering this state.
     */
    data class Loading(override val serverId: Int, override val url: String, val path: String? = null) :
        FrontendViewState

    /**
     * Content state when the WebView is displaying the Home Assistant frontend.
     */
    data class Content(
        override val serverId: Int,
        override val url: String,
        val serverHandleInsets: Boolean = false,
        val nightModeTheme: NightModeTheme? = null,
        val statusBarColor: Color? = null,
        val backgroundColor: Color? = null,
    ) : FrontendViewState

    /**
     * Error state when connection to the server fails.
     */
    data class Error(override val serverId: Int, override val url: String, val error: FrontendConnectionError) :
        FrontendViewState

    /**
     * Insecure connection state when HTTP is not allowed.
     *
     * This state occurs when the server URL uses HTTP (not HTTPS),
     * the device is not on the home network, and the user has not
     * explicitly allowed insecure connections.
     *
     * The [url] is set to about:blank to clear the webview of any previously loaded URL
     */
    data class Insecure(
        override val serverId: Int,
        val missingHomeSetup: Boolean = true,
        val missingLocation: Boolean = true,
    ) : FrontendViewState {
        override val url: String = BLANK_URL
    }

    /**
     * Security level required state when user must configure their insecure connection preference.
     *
     * This state occurs when the server has a plain text (HTTP) URL and the user has not yet
     * set their preference for allowing insecure connections. The user must configure this
     * setting before the frontend can load.
     *
     * The [url] is set to about:blank to clear the webview of any previously loaded URL
     */
    data class SecurityLevelRequired(override val serverId: Int) : FrontendViewState {
        override val url: String = BLANK_URL
    }
}
