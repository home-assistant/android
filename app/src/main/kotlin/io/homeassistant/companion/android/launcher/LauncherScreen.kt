package io.homeassistant.companion.android.launcher

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.homeassistant.companion.android.common.R as commonR

@Composable
fun NetworkUnavailableDialog(onBackClick: () -> Unit) {
    AlertDialog(
        onDismissRequest = onBackClick,
        title = { Text(stringResource(commonR.string.error_connection_failed)) },
        text = { Text(stringResource(commonR.string.error_connection_failed_no_network)) },
        confirmButton = {},
    )
}

@Composable
fun WearUnsupportedDialog(onBackClick: () -> Unit) {
    AlertDialog(
        onDismissRequest = onBackClick,
        title = { Text(stringResource(commonR.string.wear_unsupported_title)) },
        text = { Text(stringResource(commonR.string.wear_unsupported_message)) },
        confirmButton = {},
    )
}
