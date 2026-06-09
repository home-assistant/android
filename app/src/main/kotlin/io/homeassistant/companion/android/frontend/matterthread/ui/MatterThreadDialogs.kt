package io.homeassistant.companion.android.frontend.matterthread.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.composable.HALoading
import io.homeassistant.companion.android.common.compose.composable.HAPlainButton
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.frontend.dialog.FrontendDialog
import io.homeassistant.companion.android.frontend.matterthread.MatterThreadTerminal

/**
 * Non-dismissable progress dialog rendered while a Thread credential export is preparing.
 */
@Composable
internal fun MatterThreadProgressDialogContent() {
    AlertDialog(
        onDismissRequest = {},
        title = { Text(text = stringResource(commonR.string.app_name), style = HATextStyle.HeadlineMedium) },
        text = {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                HALoading()
            }
        },
        confirmButton = {
            Text(
                text = stringResource(commonR.string.thread_debug_active),
                style = HATextStyle.Body,
                modifier = Modifier.padding(horizontal = HADimens.SPACE2),
            )
        },
    )
}

/**
 * Informational terminal dialog — single OK button.
 */
@Composable
internal fun MatterThreadTerminalDialogContent(dialog: FrontendDialog.MatterThreadTerminalDialog) {
    AlertDialog(
        onDismissRequest = dialog.onDismiss,
        title = { Text(text = stringResource(commonR.string.app_name), style = HATextStyle.HeadlineMedium) },
        text = { Text(text = stringResource(dialog.terminal.messageRes), style = HATextStyle.Body) },
        confirmButton = {
            HAPlainButton(text = stringResource(commonR.string.ok), onClick = dialog.onDismiss)
        },
    )
}

@Preview
@Composable
private fun PreviewThreadNoDataset() {
    HAThemeForPreview {
        MatterThreadTerminalDialogContent(
            FrontendDialog.MatterThreadTerminalDialog(
                terminal = MatterThreadTerminal.Dialog.ThreadNoDataset,
                onDismiss = {},
            ),
        )
    }
}

@Preview
@Composable
private fun PreviewThreadNotConnected() {
    HAThemeForPreview {
        MatterThreadTerminalDialogContent(
            FrontendDialog.MatterThreadTerminalDialog(
                terminal = MatterThreadTerminal.Dialog.ThreadNotConnected,
                onDismiss = {},
            ),
        )
    }
}

@Preview
@Composable
private fun PreviewProgress() {
    HAThemeForPreview {
        MatterThreadProgressDialogContent()
    }
}
