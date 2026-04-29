package io.homeassistant.companion.android.frontend.dialog

import android.content.Context

/**
 * Represents a dialog to be displayed.
 */
sealed interface FrontendDialog {

    /**
     * A Simple dialog with a message and confirm/cancel buttons.
     *
     * @param message The message displayed in the dialog
     * @param onConfirm Called when the user taps confirm
     * @param onCancel Called when the user taps Cancel or dismisses
     */
    data class Confirm(val message: String, val onConfirm: () -> Unit, val onCancel: () -> Unit) : FrontendDialog

    /**
     * An HTTP Basic Auth dialog with username, password, and remember fields.
     *
     * @param host The host requesting authentication
     * @param message Provides the formatted message using a [Context] for string resource resolution
     * @param isAuthError `true` when shown after previously stored credentials were rejected by the server.
     * @param onProceed Called with username, password, and remember flag when user confirms
     * @param onCancel Called when user cancels or dismisses the dialog
     */
    data class HttpAuth(
        val host: String,
        val message: (Context) -> String,
        val isAuthError: Boolean,
        val onProceed: (username: String, password: String, remember: Boolean) -> Unit,
        val onCancel: () -> Unit,
    ) : FrontendDialog
}
