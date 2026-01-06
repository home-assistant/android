package io.homeassistant.companion.android.launch

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme

@Composable
fun NetworkUnavailableDialog(onBackClick: () -> Unit) {
    ErrorDialog(
        title = stringResource(commonR.string.error_connection_failed),
        content = stringResource(commonR.string.error_connection_failed_no_network),
        onBackClick = onBackClick,
    )
}

@Composable
fun WearUnsupportedDialog(onBackClick: () -> Unit) {
    ErrorDialog(
        title = stringResource(commonR.string.wear_unsupported_title),
        content = stringResource(commonR.string.wear_unsupported_message),
        onBackClick = onBackClick,
    )
}

@Composable
private fun ErrorDialog(title: String, content: String, onBackClick: () -> Unit) {
    AlertDialog(
        onDismissRequest = onBackClick,
        title = { Text(title) },
        text = { Text(content) },
        containerColor = LocalHAColorScheme.current.colorSurfaceDefault,
        textContentColor = LocalHAColorScheme.current.colorTextSecondary,
        titleContentColor = LocalHAColorScheme.current.colorTextSecondary,
        confirmButton = {},
    )
}
