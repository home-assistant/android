package io.homeassistant.companion.android.settings.ssid.views

import android.net.wifi.WifiManager
import android.os.Build
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Switch
import androidx.compose.material.SwitchDefaults
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.SettingsEthernet
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mikepenz.iconics.compose.Image
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.network.WifiHelper
import io.homeassistant.companion.android.util.compose.HaAlertInfo
import io.homeassistant.companion.android.util.compose.HaAlertWarning
import io.homeassistant.companion.android.util.plus
import io.homeassistant.companion.android.util.safeBottomPaddingValues

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun SsidView(
    wifiSsids: List<String>,
    canReadWifi: Boolean,
    ethernet: Boolean?,
    vpn: Boolean?,
    prioritizeInternal: Boolean,
    usingWifi: Boolean,
    activeSsid: String?,
    activeBssid: String?,
    onAddWifiSsid: (String) -> Boolean,
    onRemoveWifiSsid: (String) -> Unit,
    onRequestPermission: () -> Unit,
    onSetEthernet: (Boolean) -> Unit,
    onSetVpn: (Boolean) -> Unit,
    onSetPrioritize: (Boolean) -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(vertical = 16.dp) + safeBottomPaddingValues(applyHorizontal = false),
    ) {
        item("intro") {
            Column {
                Text(
                    text = stringResource(commonR.string.manage_ssids_introduction),
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 16.dp),
                )
                SsidSubheader(
                    title = stringResource(commonR.string.manage_ssids_wifi),
                    icon = Icons.Default.Wifi,
                    checked = null,
                    onClicked = null,
                )
                if (canReadWifi) {
                    SsidInput(onAddWifiSsid)
                } else {
                    Box(Modifier.padding(horizontal = 16.dp)) {
                        HaAlertWarning(
                            message = stringResource(commonR.string.manage_ssids_permission),
                            action = stringResource(commonR.string.allow),
                            onActionClicked = onRequestPermission,
                        )
                    }
                }
            }
        }

        if (
            activeSsid?.isNotBlank() == true &&
            wifiSsids.none { it == activeSsid } &&
            (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || activeSsid !== WifiManager.UNKNOWN_SSID)
        ) {
            item("ssid.suggestion") {
                Chip(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    onClick = { onAddWifiSsid(activeSsid) },
                ) {
                    Icon(
                        imageVector = Icons.Default.Wifi,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = stringResource(commonR.string.add_ssid_name_suggestion, activeSsid),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
        itemsIndexed(
            items = wifiSsids,
            key = { index: Int, item: String ->
                if (wifiSsids.count { it == item } == 1) "ssid.item.$item" else "ssid.index.$index"
            },
        ) { _, it ->
            val connected = remember(it, activeSsid, activeBssid, usingWifi) {
                usingWifi &&
                    (
                        it == activeSsid ||
                            (
                                it.startsWith(WifiHelper.BSSID_PREFIX) &&
                                    it.removePrefix(WifiHelper.BSSID_PREFIX).equals(activeBssid, ignoreCase = true)
                                )
                        )
            }
            Row(
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .padding(horizontal = 16.dp)
                    .animateItem(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (connected) {
                    Image(
                        asset = CommunityMaterial.Icon3.cmd_wifi_check,
                        colorFilter = ColorFilter.tint(colorResource(commonR.color.colorAccent)),
                    )
                    Spacer(Modifier.width(16.dp))
                }
                Text(
                    text =
                    if (it.startsWith(WifiHelper.BSSID_PREFIX)) {
                        it.removePrefix(WifiHelper.BSSID_PREFIX)
                    } else {
                        it
                    },
                    fontFamily =
                    if (it.startsWith(WifiHelper.BSSID_PREFIX)) {
                        FontFamily.Monospace
                    } else {
                        null
                    },
                    modifier = Modifier
                        .padding(end = 16.dp)
                        .weight(1f),
                )
                Icon(
                    imageVector = Icons.Default.Clear,
                    contentDescription = stringResource(commonR.string.remove_ssid),
                    tint = colorResource(commonR.color.colorWarning),
                    modifier = Modifier
                        .clickable { onRemoveWifiSsid(it) }
                        .size(48.dp)
                        .padding(all = 12.dp),
                )
            }
        }

        item("vpn") {
            SsidSubheader(
                title = stringResource(commonR.string.manage_ssids_vpn),
                icon = Icons.Default.VpnKey,
                checked = vpn,
                onClicked = { onSetVpn(it) },
            )
        }

        item("ethernet") {
            Column {
                Spacer(Modifier.height(16.dp))
                SsidSubheader(
                    title = stringResource(commonR.string.manage_ssids_ethernet),
                    icon = Icons.Default.SettingsEthernet,
                    checked = ethernet,
                    onClicked = { onSetEthernet(it) },
                )
            }
        }

        if (wifiSsids.isNotEmpty() || ethernet == true || vpn == true) {
            item("warn") {
                Box(
                    Modifier
                        .padding(horizontal = 16.dp)
                        .padding(top = 32.dp)
                        .animateItem(),
                ) {
                    HaAlertInfo(
                        message = stringResource(commonR.string.manage_ssids_warning),
                        action = null,
                        onActionClicked = null,
                    )
                }
            }
        }

        item("prioritize") {
            Column {
                Spacer(modifier = Modifier.height(48.dp))
                Divider(modifier = Modifier.padding(horizontal = 16.dp))
                SsidPrioritizeInternal(
                    prioritize = prioritizeInternal,
                    onChanged = onSetPrioritize,
                )
            }
        }
    }
}

@Composable
fun SsidSubheader(
    title: String,
    icon: ImageVector,
    checked: Boolean?,
    onClicked: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val modifier = if (onClicked != null) {
        modifier.then(
            Modifier
                .clickable { checked?.let { onClicked(!it) } ?: onClicked(true) }
                .heightIn(min = 56.dp)
                .padding(horizontal = 16.dp),
        )
    } else {
        modifier.then(
            Modifier
                .heightIn(min = 56.dp)
                .padding(horizontal = 16.dp),
        )
    }
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
        )
        Text(
            text = title,
            modifier = Modifier.padding(start = 16.dp).weight(1f),
            style = MaterialTheme.typography.subtitle1,
        )
        if (onClicked != null) {
            Switch(
                checked = checked == true,
                onCheckedChange = null,
                colors = SwitchDefaults.colors(
                    uncheckedThumbColor = colorResource(commonR.color.colorSwitchUncheckedThumb),
                ),
            )
        }
    }
}

@Composable
fun SsidInput(onSubmit: (String) -> Boolean) {
    val keyboardController = LocalSoftwareKeyboardController.current
    Row(modifier = Modifier.padding(horizontal = 16.dp)) {
        var ssidInput by remember { mutableStateOf("") }
        var ssidError by remember { mutableStateOf(false) }

        TextField(
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
                    ssidError = !onSubmit(ssidInput)
                    if (!ssidError) ssidInput = ""
                },
            ),
            isError = ssidError,
            trailingIcon = if (ssidError) {
                {
                    Icon(
                        imageVector = Icons.Default.Error,
                        contentDescription = stringResource(commonR.string.manage_ssids_input_exists),
                    )
                }
            } else {
                null
            },
            modifier = Modifier.weight(1f),
        )
        Button(
            modifier = Modifier
                .height(56.dp) // align with TextField: 56
                .padding(start = 8.dp, top = 0.dp),
            onClick = {
                keyboardController?.hide()
                ssidError = !onSubmit(ssidInput)
                if (!ssidError) ssidInput = ""
            },
        ) {
            Text(stringResource(commonR.string.add_ssid))
        }
    }
}

@Composable
fun SsidPrioritizeInternal(prioritize: Boolean, onChanged: (Boolean) -> Unit) {
    var prioritizeDropdown by remember { mutableStateOf(false) }
    Box {
        Column(
            modifier = Modifier
                .clickable { prioritizeDropdown = true }
                .fillMaxWidth()
                .padding(all = 16.dp),
        ) {
            Text(
                text = stringResource(commonR.string.prioritize_internal_title),
                style = MaterialTheme.typography.body1,
            )
            CompositionLocalProvider(LocalContentAlpha provides ContentAlpha.medium) {
                Text(
                    text = stringResource(
                        if (prioritize) {
                            commonR.string.prioritize_internal_on
                        } else {
                            commonR.string.prioritize_internal_off
                        },
                    ),
                    style = MaterialTheme.typography.body2,
                )
            }
        }
        if (prioritizeDropdown) {
            DropdownMenu(
                expanded = true,
                onDismissRequest = { prioritizeDropdown = false },
                modifier = Modifier.fillMaxWidth(),
            ) {
                DropdownMenuItem(onClick = {
                    onChanged(false)
                    prioritizeDropdown = false
                }) {
                    Text(stringResource(commonR.string.prioritize_internal_off))
                }
                DropdownMenuItem(onClick = {
                    onChanged(true)
                    prioritizeDropdown = false
                }) {
                    Text(stringResource(commonR.string.prioritize_internal_on_expanded))
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
        canReadWifi = true,
        ethernet = null,
        vpn = null,
        prioritizeInternal = false,
        activeSsid = "home-assistant-wifi",
        activeBssid = "02:00:00:00:00:00",
        usingWifi = true,
        onAddWifiSsid = { true },
        onRemoveWifiSsid = {},
        onRequestPermission = {},
        onSetEthernet = {},
        onSetVpn = {},
        onSetPrioritize = {},
    )
}

@Preview
@Composable
private fun PreviewSsidViewItems() {
    SsidView(
        wifiSsids = listOf("home-assistant-wifi", "wifi-one", "BSSID:1A:2B:3C:4D:5E:6F"),
        canReadWifi = true,
        ethernet = false,
        vpn = true,
        prioritizeInternal = false,
        activeSsid = "home-assistant-wifi",
        activeBssid = "02:00:00:00:00:00",
        usingWifi = true,
        onAddWifiSsid = { true },
        onRemoveWifiSsid = {},
        onRequestPermission = {},
        onSetEthernet = {},
        onSetVpn = {},
        onSetPrioritize = {},
    )
}
