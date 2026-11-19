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
internal fun InformationDialog(pendingDialog: FrontendDialog.Information, onOpenExternalLink: (String) -> Unit) {
    AlertDialog(
        onDismissRequest = pendingDialog.onDismiss,
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
            HAPlainButton(stringResource(commonR.string.ok), pendingDialog.onDismiss)
        },
    )
}

@Composable
@Preview
private fun PreviewInformationDialog() {
    HAThemeForPreview {
        InformationDialog(
            FrontendDialog.Information("This code is already paired", onDismiss = {}),
            onOpenExternalLink = {},
        )
    }
}

@Composable
@Preview
private fun PreviewInformationDialogWithMoreInfo() {
    HAThemeForPreview {
        InformationDialog(
            FrontendDialog.Information(
                "\"My network\" has been added, but this device still prefers a different Thread network.",
                onDismiss = {},
                moreInfoUrl = "https://companion.home-assistant.io/",
            ),
            onOpenExternalLink = {},
        )
    }
}
