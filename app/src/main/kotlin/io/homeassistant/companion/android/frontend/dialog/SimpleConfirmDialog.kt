package io.homeassistant.companion.android.frontend.dialog

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.composable.HAPlainButton
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview

@Composable
internal fun SimpleConfirmDialog(pendingDialog: FrontendDialog.Confirm, onOpenExternalLink: (String) -> Unit) {
    AlertDialog(
        onDismissRequest = pendingDialog.onCancel,
        title = { Text(text = stringResource(commonR.string.app_name), style = HATextStyle.HeadlineMedium) },
        text = {
            Column {
                Text(text = pendingDialog.message, style = HATextStyle.Body)
                pendingDialog.moreInfoUrl?.let { url ->
                    HAPlainButton(stringResource(commonR.string.learn_more), { onOpenExternalLink(url) })
                }
            }
        },
        confirmButton = {
            HAPlainButton(stringResource(commonR.string.ok), pendingDialog.onConfirm)
        },
        dismissButton = {
            HAPlainButton(stringResource(commonR.string.cancel), pendingDialog.onCancel)
        },
    )
}

@Composable
@Preview
private fun PreviewSimpleConfirmDialog() {
    HAThemeForPreview {
        SimpleConfirmDialog(
            FrontendDialog.Confirm("Hello world", onConfirm = {}, onCancel = {}),
            onOpenExternalLink = {},
        )
    }
}

@Composable
@Preview
private fun PreviewSimpleConfirmDialogWithMoreInfo() {
    HAThemeForPreview {
        SimpleConfirmDialog(
            FrontendDialog.Confirm(
                "\"My network\" will be added to the Thread credentials stored on this device. Continue?",
                onConfirm = {},
                onCancel = {},
                moreInfoUrl = "https://companion.home-assistant.io/",
            ),
            onOpenExternalLink = {},
        )
    }
}
