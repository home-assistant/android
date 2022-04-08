package io.homeassistant.companion.android.nfc.views

import android.annotation.SuppressLint
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.homeassistant.companion.android.common.R as commonR

@SuppressLint("HardwareIds")
@Composable
fun NfcEditView(
    identifier: MutableState<String?>,
    onDuplicateClicked: () -> Unit,
    onFireEventClicked: () -> Unit
) {
    val context = LocalContext.current
    val deviceId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
    val triggerExample = "- platform: event\n  event_type: tag_scanned\n  event_data:\n    device_id: $deviceId\n    tag_id: ${identifier.value}"

    LazyColumn(contentPadding = PaddingValues(all = 16.dp)) {
        item {
            Text(
                text = stringResource(commonR.string.nfc_tag_identifier),
                style = MaterialTheme.typography.subtitle1,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        item {
            NfcCodeContainer(text = identifier.value ?: "")
        }
        item {
            Row(modifier = Modifier.padding(top = 8.dp)) {
                Button(
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .weight(1f),
                    onClick = onDuplicateClicked
                ) {
                    Text(stringResource(commonR.string.nfc_btn_create_duplicate))
                }
                Button(
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .weight(1f),
                    onClick = onFireEventClicked
                ) {
                    Text(stringResource(commonR.string.nfc_btn_fire_event))
                }
            }
        }
        item {
            Text(
                text = stringResource(commonR.string.nfc_example_trigger),
                style = MaterialTheme.typography.subtitle1,
                modifier = Modifier.padding(top = 32.dp, bottom = 8.dp)
            )
        }
        item {
            NfcCodeContainer(text = triggerExample)
        }
        item {
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    val sendIntent: Intent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_TEXT, triggerExample)
                        type = "text/plain"
                    }
                    val shareIntent = Intent.createChooser(sendIntent, null)
                    context.startActivity(shareIntent)
                }
            ) {
                Text(stringResource(commonR.string.nfc_btn_share))
            }
        }
    }
}

@Composable
fun NfcCodeContainer(
    text: String
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = colorResource(commonR.color.colorCodeBackground),
        modifier = Modifier.fillMaxWidth()
    ) {
        SelectionContainer {
            Text(
                text = text,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
            )
        }
    }
}
