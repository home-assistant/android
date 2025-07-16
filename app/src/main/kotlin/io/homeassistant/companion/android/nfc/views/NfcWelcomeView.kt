package io.homeassistant.companion.android.nfc.views

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.util.plus
import io.homeassistant.companion.android.util.safeBottomPaddingValues

@Composable
fun NfcWelcomeView(isNfcEnabled: Boolean, onReadClicked: () -> Unit, onWriteClicked: () -> Unit) {
    LazyColumn(
        contentPadding = PaddingValues(all = 16.dp) + safeBottomPaddingValues(),
    ) {
        item {
            Text(stringResource(commonR.string.nfc_welcome_message))
        }
        item {
            Row(modifier = Modifier.padding(top = 16.dp)) {
                Button(
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .weight(1f),
                    enabled = isNfcEnabled,
                    onClick = onReadClicked,
                ) {
                    Text(stringResource(commonR.string.nfc_btn_read_tag))
                }
                Button(
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .weight(1f),
                    enabled = isNfcEnabled,
                    onClick = onWriteClicked,
                ) {
                    Text(stringResource(commonR.string.nfc_btn_write_tag))
                }
            }
        }

        if (!isNfcEnabled) {
            item {
                Text(
                    text = stringResource(commonR.string.nfc_welcome_turnon),
                    modifier = Modifier.padding(top = 48.dp),
                )
            }
            item {
                val context = LocalContext.current
                TextButton(
                    contentPadding = PaddingValues(horizontal = 0.dp),
                    onClick = {
                        context.startActivity(Intent(Settings.ACTION_NFC_SETTINGS))
                    },
                ) {
                    Text(stringResource(commonR.string.settings))
                }
            }
        }
    }
}

@Preview(showSystemUi = true)
@Composable
fun NfcWelcomeViewPreview() {
    NfcWelcomeView(isNfcEnabled = true, onReadClicked = { }, onWriteClicked = {})
}
