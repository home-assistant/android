package io.homeassistant.companion.android.frontend.dialog

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.composable.HAPlainButton
import io.homeassistant.companion.android.common.compose.theme.HATextStyle

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
            AlertDialog(
                onDismissRequest = pendingDialog.onCancel,
                title = { Text(text = stringResource(commonR.string.app_name), style = HATextStyle.Headline) },
                text = { Text(text = pendingDialog.message, style = HATextStyle.Body) },
                confirmButton = {
                    HAPlainButton(stringResource(commonR.string.ok), pendingDialog.onConfirm)
                },
                dismissButton = {
                    HAPlainButton(stringResource(commonR.string.cancel), pendingDialog.onCancel)
                },
            )
        }
        null -> { /* No pending dialog */ }
    }
}
