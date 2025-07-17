package io.homeassistant.companion.android.nfc.views

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mikepenz.iconics.compose.Image
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.util.compose.MdcAlertDialog

@Composable
fun NfcWriteView(isNfcEnabled: Boolean, identifier: String?, onSetIdentifier: ((String) -> Unit)? = null) {
    var identifierDialog by remember { mutableStateOf(false) }

    if (identifierDialog && onSetIdentifier != null) {
        NfcWriteIdentifierDialog(
            onCancel = { identifierDialog = false },
            onSubmit = {
                onSetIdentifier(it)
                identifierDialog = false
            },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(all = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Image(
            asset = CommunityMaterial.Icon3.cmd_nfc_tap,
            colorFilter = ColorFilter.tint(MaterialTheme.colors.onSurface),
        )
        Text(
            text =
            if (isNfcEnabled) {
                stringResource(commonR.string.nfc_write_tag_instructions, identifier ?: "")
            } else {
                stringResource(commonR.string.nfc_write_tag_turnon)
            },
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth(0.75f)
                .padding(top = 16.dp),
        )
        if (!isNfcEnabled) {
            val context = LocalContext.current
            TextButton(onClick = {
                context.startActivity(Intent(Settings.ACTION_NFC_SETTINGS))
            }) {
                Text(stringResource(commonR.string.settings))
            }
        } else if (onSetIdentifier != null) {
            TextButton(onClick = { identifierDialog = true }) {
                Text(stringResource(commonR.string.nfc_write_tag_change))
            }
        }
    }
}

@Composable
fun NfcWriteIdentifierDialog(onCancel: () -> Unit, onSubmit: (String) -> Unit) {
    val inputValue = remember { mutableStateOf("") }

    MdcAlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(commonR.string.nfc_write_tag_enter_identifier)) },
        content = {
            TextField(
                value = inputValue.value,
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Done,
                ),
                onValueChange = { input -> inputValue.value = input },
            )
        },
        onCancel = onCancel,
        onSave = { onSubmit(inputValue.value) },
    )
}

@Preview(showSystemUi = true)
@Composable
fun NfcWriteViewNfcDisabledPreview() {
    NfcWriteView(isNfcEnabled = false, identifier = "identifier")
}

@Preview(showSystemUi = true)
@Composable
fun NfcWriteViewNfcEnabledPreview() {
    NfcWriteView(isNfcEnabled = true, identifier = "identifier", onSetIdentifier = {})
}
