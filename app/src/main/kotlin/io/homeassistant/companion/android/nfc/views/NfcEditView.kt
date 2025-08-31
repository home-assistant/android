package io.homeassistant.companion.android.nfc.views

import android.annotation.SuppressLint
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.Button
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.util.plus
import io.homeassistant.companion.android.util.safeBottomPaddingValues

@SuppressLint("HardwareIds")
@Composable
fun NfcEditView(
    identifier: String?,
    showDeviceSample: Boolean,
    onDuplicateClicked: () -> Unit,
    onFireEventClicked: () -> Unit,
) {
    val context = LocalContext.current
    LazyColumn(
        contentPadding = PaddingValues(all = 16.dp) + safeBottomPaddingValues(),
    ) {
        item {
            Text(
                text = stringResource(commonR.string.nfc_tag_identifier),
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        item {
            NfcCodeContainer(text = identifier ?: "")
        }
        item {
            Row(modifier = Modifier.padding(top = 8.dp)) {
                Button(
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .weight(1f),
                    onClick = onDuplicateClicked,
                ) {
                    Text(stringResource(commonR.string.nfc_btn_create_duplicate))
                }
                Button(
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .weight(1f),
                    onClick = onFireEventClicked,
                ) {
                    Text(stringResource(commonR.string.nfc_btn_fire_event))
                }
            }
        }

        val deviceId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        val tagTriggerExample = "- platform: tag\n  tag_id: $identifier"
        val deviceTriggerExample = "- platform: tag\n  tag_id: $identifier\n  device_id: $deviceId"
        item {
            Text(
                text = stringResource(commonR.string.nfc_trigger_summary),
                modifier = Modifier.padding(top = 48.dp, bottom = 8.dp),
            )
        }
        item {
            NfcTriggerExample(
                modifier = Modifier.padding(bottom = 8.dp),
                description = if (showDeviceSample) stringResource(commonR.string.nfc_trigger_any) else "",
                example = tagTriggerExample,
            )
        }
        if (showDeviceSample) {
            item {
                NfcTriggerExample(
                    description = stringResource(commonR.string.nfc_trigger_device),
                    example = deviceTriggerExample,
                )
            }
        }
    }
}

@Composable
fun NfcCodeContainer(text: String) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = colorResource(commonR.color.colorCodeBackground),
        modifier = Modifier.fillMaxWidth(),
    ) {
        SelectionContainer {
            Text(
                text = text,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
fun NfcTriggerExample(modifier: Modifier = Modifier, description: String, example: String) {
    val context = LocalContext.current
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = description,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                modifier = Modifier.padding(all = 8.dp),
                onClick = {
                    val sendIntent: Intent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_TEXT, example)
                        type = "text/plain"
                    }
                    val shareIntent = Intent.createChooser(sendIntent, null)
                    context.startActivity(shareIntent)
                },
            ) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = stringResource(commonR.string.nfc_btn_share),
                )
            }
        }
        NfcCodeContainer(text = example)
    }
}

@Preview(showSystemUi = true)
@Composable
fun NfcEditViewPreview() {
    NfcEditView(identifier = "identifier", showDeviceSample = true, onDuplicateClicked = {}, onFireEventClicked = {})
}
