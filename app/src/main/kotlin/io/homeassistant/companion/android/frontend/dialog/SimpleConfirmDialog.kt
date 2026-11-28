package io.homeassistant.companion.android.frontend.dialog

import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.net.toUri
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.composable.HAPlainButton
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview

@Composable
internal fun SimpleConfirmDialog(pendingDialog: FrontendDialog.Confirm, onOpenExternalLink: (Uri) -> Unit) {
    AlertDialog(
        onDismissRequest = pendingDialog.onCancel,
        title = { Text(text = stringResource(commonR.string.app_name), style = HATextStyle.HeadlineMedium) },
        text = {
            // Long messages can exceed the dialog height on small screens or with large fonts.
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(text = pendingDialog.message, style = HATextStyle.Body)
                pendingDialog.moreInfoUrl?.let { url ->
                    HAPlainButton(stringResource(commonR.string.learn_more), { onOpenExternalLink(url) })
                }
            }
        },
        confirmButton = {
            HAPlainButton(stringResource(pendingDialog.buttons.confirmLabel), pendingDialog.onConfirm)
        },
        dismissButton = {
            HAPlainButton(stringResource(pendingDialog.buttons.cancelLabel), pendingDialog.onCancel)
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
                moreInfoUrl = "https://companion.home-assistant.io/".toUri(),
                buttons = ConfirmButtons.YesNo,
            ),
            onOpenExternalLink = {},
        )
    }
}
