package io.homeassistant.companion.android.frontend.dialog

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
}
