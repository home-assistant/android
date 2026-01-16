package io.homeassistant.companion.android.frontend

import androidx.compose.ui.graphics.Color
import io.homeassistant.companion.android.common.data.prefs.NightModeTheme
import io.homeassistant.companion.android.frontend.error.FrontendError

/**
 * Represents the UI state of the frontend screen.
 *
 * The frontend transitions between these states as the user interacts
 * with the WebView and connects to different servers.
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
     * @property serverId The server being connected to
     * @property path Optional path to navigate to after loading
     */
    data class LoadServer(override val serverId: Int, val path: String? = null) : FrontendViewState {
        override val url: String = "about:blank"
    }

    /**
     * Loading state while the WebView is loading the URL.
     *
     * The connection timeout starts when entering this state.
     *
     * @property serverId The server being connected to
     * @property url The URL being loaded
     * @property path Optional path to navigate to after loading
     */
    data class Loading(override val serverId: Int, override val url: String, val path: String? = null) :
        FrontendViewState

    /**
     * Content state when the WebView is displaying the Home Assistant frontend.
     *
     * @property serverId The connected server ID
     * @property url The current URL being displayed
     * @property serverHandleInsets Whether the server handles safe area insets (2025.12+)
     * @property nightModeTheme The night mode theme preference
     * @property statusBarColor Color for the status bar, extracted from the HA theme
     * @property backgroundColor Background color, extracted from the HA theme
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
     *
     * @property serverId The server that failed to connect
     * @property url The URL that failed to load
     * @property error The error details
     */
    data class Error(override val serverId: Int, override val url: String, val error: FrontendError) :
        FrontendViewState

    /**
     * Insecure connection state when HTTP is not allowed.
     *
     * This state occurs when the server URL uses HTTP (not HTTPS),
     * the device is not on the home network, and the user has not
     * explicitly allowed insecure connections.
     *
     * @property serverId The server with the insecure connection
     */
    data class Insecure(override val serverId: Int) : FrontendViewState {
        override val url: String = ""
    }

    /**
     * Security level required state when user must configure their insecure connection preference.
     *
     * This state occurs when the server has a plain text (HTTP) URL and the user has not yet
     * set their preference for allowing insecure connections. The user must configure this
     * setting before the frontend can load.
     *
     * When this state is emitted, the UI should navigate to the security level configuration screen.
     *
     * @property serverId The server requiring security level configuration
     */
    data class SecurityLevelRequired(override val serverId: Int) : FrontendViewState {
        override val url: String = ""
    }
}
