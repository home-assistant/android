package io.homeassistant.companion.android.improv.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Button
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.wifi.improv.DeviceState
import com.wifi.improv.ErrorState
import com.wifi.improv.ImprovDevice
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.util.compose.ModalBottomSheet

@Composable
fun ImprovSheetView(
    screenState: ImprovSheetState,
    onConnectToDevice: (ImprovDevice) -> Unit,
    onConnectToWifi: (String, String) -> Unit,
    onRestart: () -> Unit,
    onDismiss: () -> Unit
) {
    var selected by rememberSaveable { mutableStateOf<String?>(null) }

    val title = if (screenState.errorState == ErrorState.UNABLE_TO_CONNECT) {
        "Unable to connect"
    } else if (screenState.errorState == ErrorState.NOT_AUTHORIZED) {
        "Not authorized"
    } else if (screenState.errorState == ErrorState.UNKNOWN_COMMAND) {
        "Unknown command"
    } else if (screenState.errorState == ErrorState.UNKNOWN) {
        "Unknown error, please try again"
    } else if (screenState.deviceState == DeviceState.AUTHORIZATION_REQUIRED) {
        "Please authorize your device to continue"
    } else if (screenState.deviceState == DeviceState.AUTHORIZED) {
        "Connect to WiFi"
    } else if (screenState.deviceState == DeviceState.PROVISIONING) {
        "Connecting to Wi-Fi..."
    } else if (screenState.deviceState == DeviceState.PROVISIONED) {
        "Wi-Fi connected successfully"
    } else if (selected != null) {
        "Connecting..."
    } else {
        "Devices ready to set up"
    }

    ModalBottomSheet(
        title = title
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp)
                .heightIn(min = 120.dp)
        ) {
            if (screenState.scanning) {
                screenState.devices.forEach {
                    ImprovDeviceRow(
                        device = it,
                        onClick = { device ->
                            selected = device.address
                            onConnectToDevice(device)
                        }
                    )
                }
            } else if (screenState.deviceState == DeviceState.AUTHORIZED) {
                ImprovWifiInput(onSubmit = onConnectToWifi)
            } else if (screenState.deviceState == DeviceState.PROVISIONED) {
                Button(
                    onClick = onDismiss
                ) {
                    Text(stringResource(commonR.string.continue_connect))
                }
            } else if (screenState.errorState != null && screenState.errorState != ErrorState.NO_ERROR) {
                Button(
                    onClick = {
                        selected = null
                        onRestart()
                    }
                ) {
                    Text(stringResource(commonR.string.continue_connect))
                }
            } else {
                Box(Modifier.height(64.dp).align(Alignment.CenterHorizontally)) {
                    CircularProgressIndicator()
                }
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
                label = { Text("Network name") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(4.dp))
            TextField(
                value = passwordInput,
                onValueChange = { passwordInput = it },
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    val image = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                    val description = if (passwordVisible) "Hide password" else "Show password"

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
