package io.homeassistant.companion.android.frontend.navigation

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
     * Show a snackbar with the given string resource message.
     *
     * @param messageResId String resource ID for the message to display
     */
    data class ShowSnackbar(@StringRes val messageResId: Int) : FrontendEvent

    /** Navigate to the app settings screen. */
    data object NavigateToSettings : FrontendEvent

    /** Navigate to the assist settings screen. */
    data object NavigateToAssistSettings : FrontendEvent

    /** Navigate to the voice assistant (Assist) screen. */
    data class NavigateToAssist(val serverId: Int, val pipelineId: String?, val startListening: Boolean) :
        FrontendEvent

    /** Open a URI externally using the host-provided external link handler. */
    data class OpenExternalLink(val uri: Uri) : FrontendEvent

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
}
