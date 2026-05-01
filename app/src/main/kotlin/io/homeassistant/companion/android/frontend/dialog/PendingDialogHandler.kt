package io.homeassistant.companion.android.frontend.dialog

import androidx.compose.runtime.Composable

/**
 * Routes the current [FrontendDialog] to the appropriate Compose dialog.
 *
 * Each [FrontendDialog] subtype carries its own result callbacks, so this handler
 * only needs to map the type to the right composable.
 *
 * New dialog types (e.g. alert, prompt) can be added as new branches.
 *
 * @param pendingDialog The current dialog to show, or null if none
 */
@Composable
internal fun PendingDialogHandler(pendingDialog: FrontendDialog?) {
    when (pendingDialog) {
        is FrontendDialog.Confirm -> {
            SimpleConfirmDialog(pendingDialog)
        }
        null -> { /* No pending dialog */ }
    }
}
