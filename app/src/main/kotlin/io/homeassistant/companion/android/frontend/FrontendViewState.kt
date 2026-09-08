package io.homeassistant.companion.android.frontend

import androidx.compose.ui.graphics.Color
import io.homeassistant.companion.android.common.data.prefs.NightModeTheme
import io.homeassistant.companion.android.frontend.barcode.BarcodeScannerUiState
import io.homeassistant.companion.android.frontend.error.ErrorAction
import io.homeassistant.companion.android.frontend.error.FrontendConnectionError
import io.homeassistant.companion.android.frontend.exoplayer.ExoPlayerUiState
import io.homeassistant.companion.android.frontend.improv.ImprovUIState
import io.homeassistant.companion.android.frontend.navigation.FrontendTarget
import io.homeassistant.companion.android.util.compose.webview.BLANK_URL

/**
 * Represents the persistent UI state of the frontend screen.
 *
 * Use this for states that determine what UI is rendered and should persist until a user action
 * or system event triggers a transition (e.g., loading indicators, error screens, security
 * prompts).
 *
 * For one-shot events (e.g., opening Settings, showing a snackbar), use
 * [io.homeassistant.companion.android.frontend.navigation.FrontendEvent] instead.
 * Events are consumed once and don't persist in state.
 */
sealed interface FrontendViewState {

    /** The server ID associated with this state */
    val serverId: Int

    /**
     * The URL being loaded or displayed, or about:blank to clear the WebView of any previously
     * loaded URL in the states not displaying a page.
     */
    val url: String

    /**
     * Initial state before the URL is determined.
     *
     * This state is shown while resolving the server URL.
     * The connection timeout does not start until transitioning to [Loading].
     *
     * [target] is the frontend page to open once the frontend is ready.
     */
    data class LoadServer(override val serverId: Int, val target: FrontendTarget = FrontendTarget.Default) :
        FrontendViewState {
        override val url: String = BLANK_URL
    }

    /**
     * Loading state while the WebView is loading the URL.
     *
     * The connection timeout starts when entering this state.
     *
     * [connected] is set once the external bus handshake completes on servers that report the
     * `loaded` event.
     *
     * [target] is the frontend page to open once the frontend is ready.
     */
    data class Loading(
        override val serverId: Int,
        override val url: String,
        val target: FrontendTarget = FrontendTarget.Default,
        val connected: Boolean = false,
    ) : FrontendViewState

    /**
     * Content state when the WebView is displaying the Home Assistant frontend.
     *
     * [serverHandleInsets] is whether the server's frontend handles safe-area insets itself.
     */
    data class Content(
        override val serverId: Int,
        override val url: String,
        val serverHandleInsets: Boolean = false,
        val nightModeTheme: NightModeTheme? = null,
        val statusBarColor: Color? = null,
        val backgroundColor: Color? = null,
        val canGoBack: Boolean = false,
        val exoPlayerState: ExoPlayerUiState? = null,
        val improvUiState: ImprovUIState? = null,
        val barcodeScanner: BarcodeScannerUiState? = null,
    ) : FrontendViewState

    /**
     * Error state when connection to the server fails.
     *
     * [actions] are the recovery actions offered for [error].
     */
    data class Error(
        override val serverId: Int,
        override val url: String,
        val error: FrontendConnectionError,
        val actions: List<ErrorAction> = emptyList(),
    ) : FrontendViewState

    /**
     * Insecure connection state when HTTP is not allowed.
     *
     * This state occurs when the server URL uses HTTP (not HTTPS),
     * the device is not on the home network, and the user has not
     * explicitly allowed insecure connections.
     *
     * [missingHomeSetup] is true when no home network Wi-Fi SSID/BSSID is configured, and
     * [missingLocation] when the location permission needed for home network detection is not
     * granted, so the screen can offer the matching fix.
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
     */
    data class SecurityLevelRequired(override val serverId: Int) : FrontendViewState {
        override val url: String = BLANK_URL
    }
}
