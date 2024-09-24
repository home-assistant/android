package io.homeassistant.companion.android.improv.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Button
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.LocalContentColor
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
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
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import com.wifi.improv.DeviceState
import com.wifi.improv.ErrorState
import com.wifi.improv.ImprovDevice
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.util.compose.ModalBottomSheet

@Composable
fun ImprovSheetView(
    screenState: ImprovSheetState,
    onConnect: (String, String, String, String) -> Unit,
    onRestart: () -> Unit,
    onDismiss: () -> Unit
) {
    var selectedName by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedAddress by rememberSaveable { mutableStateOf<String?>(null) }
    var submittedWifi by rememberSaveable { mutableStateOf(false) }

    ModalBottomSheet(
        title = if (screenState.scanning && screenState.deviceState == null && !screenState.hasError) {
            if (selectedAddress != null) {
                stringResource(commonR.string.improv_wifi_title)
            } else {
                stringResource(commonR.string.improv_list_title)
            }
        } else {
            ""
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp)
                .heightIn(min = 160.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (selectedAddress != null && !submittedWifi) {
                ImprovWifiInput(onSubmit = { ssid, password ->
                    onConnect(selectedName ?: "", selectedAddress ?: "", ssid, password)
                    submittedWifi = true
                })
            } else if (screenState.scanning) {
                screenState.devices.forEach {
                    ImprovDeviceRow(
                        device = it,
                        onClick = { device ->
                            selectedName = device.name
                            selectedAddress = device.address
                        }
                    )
                }
            } else if (screenState.deviceState == DeviceState.PROVISIONED) {
                Image(
                    asset = CommunityMaterial.Icon3.cmd_wifi_check,
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(LocalContentColor.current),
                    modifier = Modifier.size(40.dp)
                )
                Text(
                    text = stringResource(commonR.string.improv_device_provisioned),
                    modifier = Modifier.fillMaxWidth(0.8f).padding(vertical = 16.dp),
                    textAlign = TextAlign.Center
                )
                Button(
                    onClick = onDismiss
                ) {
                    Text(stringResource(commonR.string.continue_connect))
                }
            } else if (screenState.hasError) {
                Image(
                    asset = CommunityMaterial.Icon.cmd_alert,
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(LocalContentColor.current),
                    modifier = Modifier.size(40.dp)
                )
                Text(
                    text = stringResource(
                        when (screenState.errorState) {
                            ErrorState.UNABLE_TO_CONNECT -> commonR.string.improv_error_unable_to_connect
                            ErrorState.NOT_AUTHORIZED -> commonR.string.improv_error_not_authorized
                            ErrorState.UNKNOWN_COMMAND -> commonR.string.improv_error_unknown_command
                            ErrorState.INVALID_RPC_PACKET -> commonR.string.improv_error_invalid_rpc_packet
                            else -> commonR.string.improv_error_unknown
                        }
                    ),
                    modifier = Modifier.fillMaxWidth(0.8f).padding(vertical = 16.dp),
                    textAlign = TextAlign.Center
                )
                Button(
                    onClick = {
                        selectedName = null
                        selectedAddress = null
                        submittedWifi = false
                        onRestart()
                    }
                ) {
                    Text(stringResource(commonR.string.continue_connect))
                }
            } else {
                CircularProgressIndicator()
                Text(
                    text = stringResource(
                        if (screenState.deviceState == DeviceState.AUTHORIZATION_REQUIRED) {
                            commonR.string.improv_device_authorization_required
                        } else if (screenState.deviceState == DeviceState.AUTHORIZED) {
                            commonR.string.improv_device_authorized
                        } else if (screenState.deviceState == DeviceState.PROVISIONING) {
                            commonR.string.improv_device_provisioning
                        } else if (selectedAddress != null) {
                            commonR.string.improv_device_connecting
                        } else {
                            commonR.string.state_unknown
                        }
                    ),
                    modifier = Modifier.fillMaxWidth(0.8f).padding(top = 16.dp),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun ImprovDeviceRow(
    device: ImprovDevice,
    onClick: (ImprovDevice) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clickable { onClick(device) },
        verticalArrangement = Arrangement.Center
    ) {
        Text(device.name.ifBlank { device.address })
    }
}

@Composable
fun ImprovWifiInput(
    onSubmit: (String, String) -> Unit
) {
    var ssidInput by rememberSaveable { mutableStateOf("") }
    var passwordInput by rememberSaveable { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth()) {
            TextField(
                value = ssidInput,
                onValueChange = { ssidInput = it },
                label = { Text(stringResource(commonR.string.improv_wifi_ssid)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(4.dp))
            TextField(
                value = passwordInput,
                onValueChange = { passwordInput = it },
                label = { Text(stringResource(commonR.string.password)) },
                singleLine = true,
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    val image = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                    val description = stringResource(if (passwordVisible) commonR.string.hide_password else commonR.string.view_password)

                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(image, description)
                    }
                },
                modifier = Modifier.weight(1f)
            )
        }
        Button(
            modifier = Modifier.padding(top = 8.dp).align(Alignment.CenterHorizontally),
            enabled = ssidInput.isNotBlank() && passwordInput.isNotBlank(),
            onClick = { onSubmit(ssidInput, passwordInput) }
        ) {
            Text(stringResource(commonR.string.continue_connect))
        }
    }
}

@Preview
@Composable
fun PreviewImprovDevices() {
    ImprovSheetView(
        screenState = ImprovSheetState(
            scanning = true,
            devices = listOf(ImprovDevice("Demo device", "A1:B2:C3:D4:E5:F6")),
            deviceState = null,
            errorState = null
        ),
        onConnect = { _, _, _, _ -> },
        onRestart = {},
        onDismiss = {}
    )
}

@Preview
@Composable
fun PreviewImprovSubmitting() {
    ImprovSheetView(
        screenState = ImprovSheetState(
            scanning = false,
            devices = listOf(ImprovDevice("Demo device", "A1:B2:C3:D4:E5:F6")),
            deviceState = DeviceState.PROVISIONING,
            errorState = ErrorState.NO_ERROR
        ),
        onConnect = { _, _, _, _ -> },
        onRestart = {},
        onDismiss = {}
    )
}

@Preview
@Composable
fun PreviewImprovInvalid() {
    ImprovSheetView(
        screenState = ImprovSheetState(
            scanning = false,
            devices = listOf(ImprovDevice("Demo device", "A1:B2:C3:D4:E5:F6")),
            deviceState = DeviceState.PROVISIONING,
            errorState = ErrorState.INVALID_RPC_PACKET
        ),
        onConnect = { _, _, _, _ -> },
        onRestart = {},
        onDismiss = {}
    )
}
