package io.homeassistant.companion.android.settings.ssid.views

import android.net.wifi.WifiManager
import android.os.Build
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Button
import androidx.compose.material.Chip
import androidx.compose.material.ContentAlpha
import androidx.compose.material.Divider
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Icon
import androidx.compose.material.LocalContentAlpha
import androidx.compose.material.LocalContentColor
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.homeassistant.companion.android.common.R as commonR

@OptIn(
    ExperimentalComposeUiApi::class,
    ExperimentalMaterialApi::class,
    ExperimentalFoundationApi::class
)
@Composable
fun SsidView(
    wifiSsids: List<String>,
    prioritizeInternal: Boolean,
    activeSsid: String,
    onAddWifiSsid: (String) -> Boolean,
    onRemoveWifiSsid: (String) -> Unit,
    onSetPrioritize: (Boolean) -> Unit
) {
    val keyboardController = LocalSoftwareKeyboardController.current

    LazyColumn(contentPadding = PaddingValues(vertical = 16.dp)) {
        item("ssid.intro") {
            Column {
                Text(
                    text = stringResource(commonR.string.manage_ssids_introduction),
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 8.dp)
                )
                Row(modifier = Modifier.padding(horizontal = 16.dp)) {
                    var ssidInput by remember { mutableStateOf("") }
                    var ssidError by remember { mutableStateOf(false) }

                    OutlinedTextField(
                        value = ssidInput,
                        singleLine = true,
                        onValueChange = {
                            ssidInput = it
                            ssidError = false
                        },
                        label = { Text(stringResource(commonR.string.manage_ssids_input)) },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                keyboardController?.hide()
                                ssidError = !onAddWifiSsid(ssidInput)
                                if (!ssidError) ssidInput = ""
                            }
                        ),
                        isError = ssidError,
                        trailingIcon = if (ssidError) {
                            {
                                Icon(
                                    imageVector = Icons.Default.Error,
                                    contentDescription = stringResource(commonR.string.manage_ssids_input_exists)
                                )
                            }
                        } else null,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        modifier = Modifier
                            .height(64.dp) // align with OutlinedTextField: 56 + 8
                            .padding(start = 8.dp, top = 8.dp),
                        onClick = {
                            keyboardController?.hide()
                            ssidError = !onAddWifiSsid(ssidInput)
                            if (!ssidError) ssidInput = ""
                        }
                    ) {
                        Text(stringResource(commonR.string.add_ssid))
                    }
                }
            }
        }

        if (
            activeSsid.isNotBlank() &&
            wifiSsids.none { it == activeSsid } &&
            (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || activeSsid !== WifiManager.UNKNOWN_SSID)
        ) {
            item("ssid.suggestion") {
                Chip(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    onClick = { onAddWifiSsid(activeSsid) }
                ) {
                    Icon(
                        imageVector = Icons.Default.Wifi,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = stringResource(commonR.string.add_ssid_name_suggestion, activeSsid),
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        }
        items(wifiSsids, key = { "ssid.item.$it" }) {
            val connected = it == activeSsid
            Row(
                modifier = Modifier
                    .heightIn(min = 56.dp)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .animateItemPlacement(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Wifi,
                    contentDescription = null,
                    tint =
                    if (connected) colorResource(commonR.color.colorAccent)
                    else LocalContentColor.current.copy(alpha = LocalContentAlpha.current)
                )
                Text(
                    text = it,
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .weight(1f)
                )
                Icon(
                    imageVector = Icons.Default.Clear,
                    contentDescription = stringResource(commonR.string.remove_ssid),
                    tint = colorResource(commonR.color.colorWarning),
                    modifier = Modifier
                        .clickable { onRemoveWifiSsid(it) }
                        .size(48.dp)
                        .padding(all = 12.dp)
                )
            }
        }

        item("prioritize") {
            var prioritizeDropdown by remember { mutableStateOf(false) }

            Column {
                Spacer(modifier = Modifier.height(48.dp))
                Divider(modifier = Modifier.padding(horizontal = 16.dp))
                Box {
                    Column(
                        modifier = Modifier
                            .clickable { prioritizeDropdown = true }
                            .fillMaxWidth()
                            .padding(all = 16.dp)
                    ) {
                        Text(
                            text = stringResource(commonR.string.prioritize_internal_title),
                            style = MaterialTheme.typography.body1
                        )
                        CompositionLocalProvider(LocalContentAlpha provides ContentAlpha.medium) {
                            Text(
                                text = stringResource(
                                    if (prioritizeInternal) commonR.string.prioritize_internal_on
                                    else commonR.string.prioritize_internal_off
                                ),
                                style = MaterialTheme.typography.body2
                            )
                        }
                    }
                    if (prioritizeDropdown) {
                        DropdownMenu(
                            expanded = prioritizeDropdown,
                            onDismissRequest = { prioritizeDropdown = false },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            DropdownMenuItem(onClick = {
                                onSetPrioritize(false)
                                prioritizeDropdown = false
                            }) {
                                Text(stringResource(commonR.string.prioritize_internal_off))
                            }
                            DropdownMenuItem(onClick = {
                                onSetPrioritize(true)
                                prioritizeDropdown = false
                            }) {
                                Text(stringResource(commonR.string.prioritize_internal_on_expanded))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Preview
@Composable
private fun PreviewSsidViewEmpty() {
    SsidView(
        wifiSsids = emptyList(),
        prioritizeInternal = false,
        activeSsid = "home-assistant-wifi",
        onAddWifiSsid = { true },
        onRemoveWifiSsid = {},
        onSetPrioritize = {}
    )
}

@Preview
@Composable
private fun PreviewSsidViewItems() {
    SsidView(
        wifiSsids = listOf("home-assistant-wifi", "wifi-one", "wifi-two"),
        prioritizeInternal = false,
        activeSsid = "home-assistant-wifi",
        onAddWifiSsid = { true },
        onRemoveWifiSsid = {},
        onSetPrioritize = {}
    )
}
