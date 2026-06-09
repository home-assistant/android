package io.homeassistant.companion.android.frontend.improv.ui

import android.net.wifi.WifiManager
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mikepenz.iconics.compose.Image
import com.mikepenz.iconics.typeface.IIcon
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import com.wifi.improv.DeviceState
import com.wifi.improv.ErrorState
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.composable.HAFilledButton
import io.homeassistant.companion.android.common.compose.composable.HALoading
import io.homeassistant.companion.android.common.compose.composable.HATextField
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.common.util.SdkVersion
import io.homeassistant.companion.android.frontend.improv.ImprovUIState

/**
 * Body of the Improv Wi-Fi onboarding sheet using [screenState] to show a matching phase:
 *  - searching spinner ([ImprovUIState.SearchingDevice])
 *  - Wi-Fi credentials form ([ImprovUIState.ConfiguringDevice])
 *  - provisioning spinner with a state-dependent caption ([ImprovUIState.Provisioning])
 *  - error with retry button ([ImprovUIState.Errored])
 *  - success with continue button ([ImprovUIState.Provisioned])
 *
 * Renders content only — embed inside an
 * [io.homeassistant.companion.android.common.compose.composable.HAModalBottomSheet] (or the legacy
 * `BottomSheetDialogFragment` host) which provides the dialog frame.
 *
 * @param onConnect Invoked when the user submits Wi-Fi credentials. The device being configured is
 *   already known by the host (it's on the [ImprovUIState.ConfiguringDevice] state).
 * @param onRestart Invoked when the user taps "Try again" after an error.
 * @param onDismiss Invoked when the user taps the success/finish button. Caller should also
 *   handle bottom-sheet swipe-to-dismiss separately.
 */
@Composable
fun ImprovSheet(
    screenState: ImprovUIState,
    onConnect: (ssid: String, password: String) -> Unit,
    onRestart: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(HADimens.SPACE4)
            .heightIn(min = 160.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        when (screenState) {
            is ImprovUIState.SearchingDevice -> LoadingWithText(
                text = stringResource(commonR.string.improv_device_connecting),
            )

            is ImprovUIState.ConfiguringDevice -> ConfiguringDeviceSection(
                state = screenState,
                onConnect = onConnect,
            )

            is ImprovUIState.Provisioning if (screenState.state != DeviceState.PROVISIONED) -> LoadingWithText(
                text = stringResource(
                    when (screenState.state) {
                        DeviceState.AUTHORIZATION_REQUIRED -> commonR.string.improv_device_authorization_required
                        DeviceState.AUTHORIZED -> commonR.string.improv_device_authorized
                        DeviceState.PROVISIONING -> commonR.string.improv_device_provisioning
                        null -> commonR.string.improv_device_connecting
                    },
                ),
            )

            // remaining state from the guard it means screenState.state == DeviceState.PROVISIONED
            is ImprovUIState.Provisioning,
            is ImprovUIState.Provisioned,
            -> ProvisionedSection(onDismiss = onDismiss)

            is ImprovUIState.Errored -> ErroredSection(error = screenState.error, onRestart = onRestart)
        }
    }
}

@Composable
private fun LoadingWithText(text: String) {
    HALoading()
    Text(
        text = text,
        style = HATextStyle.Body,
        modifier = Modifier
            .padding(top = HADimens.SPACE4),
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun ColumnScope.ConfiguringDeviceSection(
    state: ImprovUIState.ConfiguringDevice,
    onConnect: (ssid: String, password: String) -> Unit,
) {
    Text(
        text = stringResource(commonR.string.improv_wifi_title),
        style = HATextStyle.HeadlineMedium,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = HADimens.SPACE4),
    )
    ImprovWifiInput(
        activeSsid = state.activeSsid.takeIfDisplayable(),
        onSubmit = onConnect,
    )
}

/**
 * Returns the SSID if it's safe to prefill into the credentials form. Filters out `null`, blank, or dummy
 * values when the app lacks the location permission.
 */
private fun String?.takeIfDisplayable(): String? = takeIf {
    !it.isNullOrBlank() && (!SdkVersion.isAtLeast(Build.VERSION_CODES.R) || it !== WifiManager.UNKNOWN_SSID)
}

@Composable
private fun ColumnScope.ErroredSection(error: ErrorState, onRestart: () -> Unit) {
    if (error != ErrorState.NO_ERROR) {
        ImprovAction(
            icon = CommunityMaterial.Icon.cmd_alert,
            text = stringResource(
                when (error) {
                    ErrorState.UNABLE_TO_CONNECT -> commonR.string.improv_error_unable_to_connect
                    ErrorState.NOT_AUTHORIZED -> commonR.string.improv_error_not_authorized
                    ErrorState.UNKNOWN_COMMAND -> commonR.string.improv_error_unknown_command
                    ErrorState.INVALID_RPC_PACKET -> commonR.string.improv_error_invalid_rpc_packet
                    ErrorState.UNKNOWN -> commonR.string.improv_error_unknown
                },
            ),
            onButtonClick = onRestart,
        )
    }
}

@Composable
private fun ColumnScope.ProvisionedSection(onDismiss: () -> Unit) {
    ImprovAction(
        icon = CommunityMaterial.Icon3.cmd_wifi_check,
        text = stringResource(commonR.string.improv_device_provisioned),
        onButtonClick = onDismiss,
    )
}

@Composable
private fun ImprovWifiInput(activeSsid: String?, onSubmit: (String, String) -> Unit, modifier: Modifier = Modifier) {
    var ssidInput by rememberSaveable { mutableStateOf(activeSsid ?: "") }
    var passwordInput by rememberSaveable { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    Column(
        modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(HADimens.SPACE2),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        HATextField(
            value = ssidInput,
            onValueChange = { ssidInput = it },
            label = { Text(stringResource(commonR.string.improv_wifi_ssid)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )
        HATextField(
            value = passwordInput,
            onValueChange = { passwordInput = it },
            label = { Text(stringResource(commonR.string.password)) },
            singleLine = true,
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(
                onDone = {
                    if (ssidInput.isNotBlank()) {
                        onSubmit(ssidInput, passwordInput)
                    }
                },
            ),
            trailingIcon = {
                val image = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                val description = stringResource(
                    if (passwordVisible) commonR.string.hide_password else commonR.string.view_password,
                )
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(image, description)
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        HAFilledButton(
            text = stringResource(commonR.string.continue_connect),
            onClick = { onSubmit(ssidInput, passwordInput) },
            enabled = ssidInput.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = HADimens.SPACE6),
        )
    }
}

@Composable
private fun ColumnScope.ImprovAction(
    icon: IIcon,
    text: String,
    onButtonClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Image(
        asset = icon,
        contentDescription = null,
        colorFilter = ColorFilter.tint(LocalHAColorScheme.current.colorOnNeutralNormal),
        modifier = Modifier.size(40.dp),
    )
    Text(
        text = text,
        style = HATextStyle.Body,
        modifier = modifier
            .padding(vertical = HADimens.SPACE4),
    )
    HAFilledButton(
        text = stringResource(commonR.string.continue_connect),
        onClick = onButtonClick,
    )
}

@Preview
@Composable
private fun PreviewImprovSearching() {
    HAThemeForPreview {
        ImprovSheet(
            screenState = ImprovUIState.SearchingDevice(deviceName = "Smart Plug"),
            onConnect = { _, _ -> },
            onRestart = {},
            onDismiss = {},
        )
    }
}

@Preview
@Composable
private fun PreviewImprovWifiInput() {
    HAThemeForPreview {
        ImprovSheet(
            screenState = ImprovUIState.ConfiguringDevice(
                deviceName = "Smart Plug",
                deviceAddress = "A1:B2:C3:D4:E5:F6",
                activeSsid = "Home Wi-Fi",
            ),
            onConnect = { _, _ -> },
            onRestart = {},
            onDismiss = {},
        )
    }
}

@Preview
@Composable
private fun PreviewImprovProvisioning() {
    HAThemeForPreview {
        ImprovSheet(
            screenState = ImprovUIState.Provisioning(
                deviceName = "Smart Plug",
                deviceAddress = "A1:B2:C3:D4:E5:F6",
                state = DeviceState.PROVISIONING,
            ),
            onConnect = { _, _ -> },
            onRestart = {},
            onDismiss = {},
        )
    }
}

@Preview
@Composable
private fun PreviewImprovErrored() {
    HAThemeForPreview {
        ImprovSheet(
            screenState = ImprovUIState.Errored(
                deviceName = "Smart Plug",
                deviceAddress = "A1:B2:C3:D4:E5:F6",
                error = ErrorState.INVALID_RPC_PACKET,
            ),
            onConnect = { _, _ -> },
            onRestart = {},
            onDismiss = {},
        )
    }
}

@Preview
@Composable
private fun PreviewImprovProvisioned() {
    HAThemeForPreview {
        ImprovSheet(
            screenState = ImprovUIState.Provisioned(domain = null),
            onConnect = { _, _ -> },
            onRestart = {},
            onDismiss = {},
        )
    }
}
