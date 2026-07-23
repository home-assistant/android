package io.homeassistant.companion.android.frontend.navigation

import android.content.IntentSender
import android.net.Uri
import androidx.annotation.StringRes

/**
 * One-shot events emitted by the ViewModel for the screen to handle.
 *
 * These events are fire-and-forget and should not persist in state. They are consumed once by
 * [FrontendEventHandler] (e.g., showing a snackbar, navigating to Settings, opening a link).
 *
 * For persistent UI states that determine what's rendered within the frontend screen, use
 * [io.homeassistant.companion.android.frontend.FrontendViewState] instead.
 */
sealed interface FrontendEvent {

    /**
     * Show a snackbar with the given string resource message and, optionally, an action button
     * whose tap fires another [FrontendEvent].
     *
     * Chaining via [Action.event] keeps `ShowSnackbar` agnostic to what the action *does*: the
     * emitter decides whether the tap opens a link, navigates somewhere, retries, etc. The
     * handler dispatches the inner event.
     *
     * @param messageResId String resource ID for the message to display
     * @param action Optional action button. `null` renders a plain message snackbar.
     */
    data class ShowSnackbar(@param:StringRes val messageResId: Int, val action: Action? = null) : FrontendEvent {

        /**
         * @param labelResId String resource for the action button label (e.g. "Get help").
         * @param event Fired when the user taps the action; routed back through
         *   [FrontendEventHandler] like any other [FrontendEvent].
         */
        data class Action(@param:StringRes val labelResId: Int, val event: FrontendEvent)
    }

    /** Navigate to the app settings screen. */
    data object NavigateToSettings : FrontendEvent

    /** Open the OS security settings screen (client-certificate installation). */
    data object OpenSecuritySettings : FrontendEvent

    /** Open the store/app page of the device's current WebView provider so the user can update it. */
    data object UpdateWebView : FrontendEvent

    /** Restart the app from scratch. */
    data object Relaunch : FrontendEvent

    /** Navigate to the assist settings screen. */
    data object NavigateToAssistSettings : FrontendEvent

    /** Navigate to the voice assistant (Assist) screen. */
    data class NavigateToAssist(val serverId: Int, val pipelineId: String?, val startListening: Boolean) :
        FrontendEvent

    /** Open a URI externally using the host-provided external link handler. */
    data class OpenExternalLink(val uri: Uri) : FrontendEvent

    /**
     * Launch an installed app by its [packageName].
     *
     * The host is responsible for opening the app store when no app with this package is installed.
     */
    data class LaunchApp(val packageName: String) : FrontendEvent

    /**
     * Parse and launch an Android `intent:` URI.
     *
     * The host is responsible for opening the app store when the intent targets a package that is not installed.
     *
     * @param intentUri The raw `intent:` URI as provided by the frontend.
     */
    data class LaunchIntent(val intentUri: String) : FrontendEvent

    /** Navigate to the developer tools settings screen */
    data object NavigateToDeveloperSettings : FrontendEvent

    /**
     * Show a bottom sheet letting the user pick among registered servers.
     *
     * Only emitted when there is more than one registered server; the host is responsible
     * for forwarding the user's selection back to the ViewModel via [FrontendViewModel.switchServer].
     */
    data object ShowServerSwitcher : FrontendEvent

    /**
     * Navigate to the NFC tag-write flow.
     *
     * The host is responsible for launching the corresponding activity contract and forwarding the
     * result back to the ViewModel via [FrontendViewModel.onNfcWriteCompleted].
     *
     * @param messageId Correlation id from the originating `tag/write` request.
     * @param tagId Optional pre-filled tag identifier.
     */
    data class NavigateToNfcWrite(val messageId: Int, val tagId: String?) : FrontendEvent

    /**
     * Request the host activity to enter or leave fullscreen (hide/show system bars).
     *
     * This is a request, not a command: the LaunchViewModel decides the actual system bar
     * visibility by combining this with the user's fullscreen preference. For instance, if
     * the preference already enables fullscreen, a `false` request won't leave fullscreen.
     */
    data class RequestFullscreen(val fullscreen: Boolean) : FrontendEvent

    /**
     * Navigate to a widget configuration screen for the given entity.
     *
     * @param entityId The entity to pre-fill in the widget configuration
     * @param widgetType The type of widget to configure
     */
    data class NavigateToWidgetConfig(val entityId: String, val widgetType: WidgetType) : FrontendEvent

    /**
     * Launch a Play Services Matter/Thread intent. The host is responsible for launching the
     * [intentSender] via an `ActivityResultLauncher` and forwarding the result back to the
     * ViewModel via [io.homeassistant.companion.android.frontend.FrontendViewModel.onMatterThreadIntentResult].
     *
     * One event covers both flows because only one launcher needs to be registered.
     */
    data class LaunchMatterThreadIntent(val intentSender: IntentSender) : FrontendEvent
}
