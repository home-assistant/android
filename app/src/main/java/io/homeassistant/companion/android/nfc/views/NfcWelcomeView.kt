package io.homeassistant.companion.android.nfc.views

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.homeassistant.companion.android.common.R as commonR

@Composable
fun NfcWelcomeView(
    onReadClicked: () -> Unit,
    onWriteClicked: () -> Unit,
) {
    LazyColumn(contentPadding = PaddingValues(all = 16.dp)) {
        item {
            Text(stringResource(commonR.string.nfc_welcome_message))
        }
        item {
            Row(modifier = Modifier.padding(top = 16.dp)) {
                Button(
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .weight(1f),
                    onClick = onReadClicked
                ) {
                    Text(stringResource(commonR.string.nfc_btn_read_tag))
                }
                Button(
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .weight(1f),
                    onClick = onWriteClicked
                ) {
                    Text(stringResource(commonR.string.nfc_btn_write_tag))
                }
            }
        }
    }
}
