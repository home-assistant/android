package io.homeassistant.companion.android.onboarding.sethomenetwork

import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SettingsEthernet
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.composable.HAAccentButton
import io.homeassistant.companion.android.common.compose.composable.HAHint
import io.homeassistant.companion.android.common.compose.composable.HASwitch
import io.homeassistant.companion.android.common.compose.composable.HATextField
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.common.compose.theme.MaxButtonWidth
import io.homeassistant.companion.android.compose.HAPreviews
import io.homeassistant.companion.android.compose.composable.HATopBar
import io.homeassistant.companion.android.onboarding.R

private val MaxContentWidth = MaxButtonWidth

@VisibleForTesting
internal const val ETHERNET_TAG = "ethernet_tag"
internal const val VPN_TAG = "vpn_tag"

@Composable
fun SetHomeNetworkScreen(
    onHelpClick: () -> Unit,
    onGotoNextScreen: () -> Unit,
    viewModel: SetHomeNetworkViewModel,
    modifier: Modifier = Modifier,
) {
    val currentWifiNetwork by viewModel.currentWifiNetwork.collectAsStateWithLifecycle()
    val isUsingVpn by viewModel.isUsingVpn.collectAsStateWithLifecycle()
    val isUsingEthernet by viewModel.isUsingEthernet.collectAsStateWithLifecycle()

    SetHomeNetworkScreen(
        onHelpClick = onHelpClick,
        onNextClick = {
            viewModel.onNextClick()
            onGotoNextScreen()
        },
        currentWifiNetwork = currentWifiNetwork,
        onCurrentWifiNetworkChange = viewModel::onCurrentWifiNetworkChange,
        showVpn = viewModel.hasVPNConnection,
        isUsingVpn = isUsingVpn,
        onUsingVpnChange = viewModel::onUsingVpnChange,
        showEthernet = viewModel.hasEthernetConnection,
        isUsingEthernet = isUsingEthernet,
        onUsingEthernetChange = viewModel::onUsingEthernetChange,
        modifier = modifier,
    )
}

@Composable
internal fun SetHomeNetworkScreen(
    onHelpClick: () -> Unit,
    onNextClick: () -> Unit,
    currentWifiNetwork: String,
    onCurrentWifiNetworkChange: (String) -> Unit,
    showVpn: Boolean,
    isUsingVpn: Boolean,
    onUsingVpnChange: (Boolean) -> Unit,
    showEthernet: Boolean,
    isUsingEthernet: Boolean,
    onUsingEthernetChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = { HATopBar(onHelpClick = onHelpClick) },
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { contentPadding ->
        SetHomeNetworkContent(
            currentWifiNetwork = currentWifiNetwork,
            onCurrentWifiNetworkChange = onCurrentWifiNetworkChange,
            showVpn = showVpn,
            isUsingVpn = isUsingVpn,
            onUsingVpnChange = onUsingVpnChange,
            showEthernet = showEthernet,
            isUsingEthernet = isUsingEthernet,
            onUsingEthernetChange = onUsingEthernetChange,
            onNextClick = onNextClick,
            modifier = Modifier.padding(contentPadding),
        )
    }
}

@Composable
private fun SetHomeNetworkContent(
    currentWifiNetwork: String,
    onCurrentWifiNetworkChange: (String) -> Unit,
    showVpn: Boolean,
    isUsingVpn: Boolean,
    onUsingVpnChange: (Boolean) -> Unit,
    showEthernet: Boolean,
    isUsingEthernet: Boolean,
    onUsingEthernetChange: (Boolean) -> Unit,
    onNextClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = HADimens.SPACE4)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(HADimens.SPACE6),
    ) {
        Header()

        WifiNetworkSSIDTextField(currentWifiNetwork, onCurrentWifiNetworkChange)

        if (showVpn) {
            SelectableOption(
                isUsingVpn,
                onUsingVpnChange,
                text = stringResource(commonR.string.manage_ssids_vpn),
                icon = Icons.Default.VpnKey,
                testTag = VPN_TAG,
            )
        }

        if (showEthernet) {
            SelectableOption(
                isUsingEthernet,
                onUsingEthernetChange,
                text = stringResource(commonR.string.manage_ssids_ethernet),
                icon = Icons.Default.SettingsEthernet,
                testTag = ETHERNET_TAG,
            )
        }

        HAHint(text = stringResource(commonR.string.manage_ssids_warning), modifier = Modifier.width(MaxContentWidth))
        Spacer(modifier = Modifier.weight(1f))

        HAAccentButton(
            text = stringResource(commonR.string.set_home_network_next),
            onClick = onNextClick,
            modifier = Modifier.fillMaxWidth().padding(bottom = HADimens.SPACE6),
        )
    }
}

@Composable
private fun ColumnScope.Header() {
    Image(
        modifier = Modifier
            .padding(top = HADimens.SPACE6),
        // Use painterResource instead of vector resource for API < 24 since it has gradients
        painter = painterResource(R.drawable.ic_location_secure),
        contentDescription = null,
    )
    Text(
        text = stringResource(commonR.string.set_home_network_title),
        style = HATextStyle.Headline,
    )
    Text(
        text = stringResource(commonR.string.set_home_network_content),
        style = HATextStyle.Body,
        modifier = Modifier.widthIn(max = MaxContentWidth),
    )
}

@Composable
private fun WifiNetworkSSIDTextField(currentWifiNetwork: String, onCurrentWifiNetworkChange: (String) -> Unit) {
    HATextField(
        value = currentWifiNetwork,
        onValueChange = onCurrentWifiNetworkChange,
        label = {
            Text(text = stringResource(commonR.string.set_home_network_wifi_name))
        },
        trailingIcon = {
            if (currentWifiNetwork.isNotEmpty()) {
                IconButton(onClick = { onCurrentWifiNetworkChange("") }) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(commonR.string.clear_text),
                    )
                }
            }
        },
    )
}

@Composable
private fun SelectableOption(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    text: String,
    testTag: String,
    icon: ImageVector,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(HADimens.SPACE4),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.widthIn(max = MaxContentWidth),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = LocalHAColorScheme.current.colorOnPrimaryNormal,
        )

        Text(
            text = text,
            style = HATextStyle.Body.copy(textAlign = TextAlign.Start),
            modifier = Modifier.weight(1f),
        )
        HASwitch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag),
        )
    }
}

@HAPreviews
@Composable
private fun SetHomeNetworkScreenPreview() {
    HAThemeForPreview {
        SetHomeNetworkScreen(
            onHelpClick = {},
            showEthernet = true,
            showVpn = true,
            isUsingVpn = true,
            isUsingEthernet = true,
            currentWifiNetwork = "SSID",
            onCurrentWifiNetworkChange = {},
            onUsingVpnChange = {},
            onUsingEthernetChange = {},
            onNextClick = {},
        )
    }
}
