package io.homeassistant.companion.android.settings.sensor.views

import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.homeassistant.companion.android.common.R
import io.homeassistant.companion.android.common.util.sensorWorkerChannel
import io.homeassistant.companion.android.database.settings.UpdateFrequencies
import io.homeassistant.companion.android.util.compose.InfoNotification

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun SensorUpdateFrequencyView(
    sensorUpdateFrequencyBattery: Int,
    sensorUpdateFrequencyPowered: Int,
    onBatteryFrequencyChanged: (Int) -> Unit,
    onPoweredFrequencyChanged: (Int) -> Unit
) {
    val scrollState = rememberScrollState()
    Box(modifier = Modifier.verticalScroll(scrollState)) {
        Column(modifier = Modifier.padding(all = 16.dp)) {
            Text(
                text = stringResource(R.string.sensor_update_frequency_description),
                modifier = Modifier.padding(bottom = 16.dp)
            )
            Divider()

            Spacer(Modifier.height(16.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                var showBatteryFrequencyDropdown by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = showBatteryFrequencyDropdown,
                    onExpandedChange = { showBatteryFrequencyDropdown = it }
                ) {
                    OutlinedTextField(
                        value = "$sensorUpdateFrequencyBattery",
                        onValueChange = {},
                        label = { Text("On Battery Frequency") },
                        readOnly = true,
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = showBatteryFrequencyDropdown)
                        }
                    )
                    ExposedDropdownMenu(
                        expanded = showBatteryFrequencyDropdown,
                        onDismissRequest = { showBatteryFrequencyDropdown = false }
                    ) {
                        UpdateFrequencies.forEach {
                            DropdownMenuItem(onClick = {
                                onBatteryFrequencyChanged(it)
                                showBatteryFrequencyDropdown = false
                                // TODO: Possibly adjust powered frequency if battery frequency set higher,
                                //       i.e. fewer mins between updates, than powered frequency
                                //       Since updating more frequently when on battery makes little sense
                            }) {
                                Text("$it minutes")
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                var showPoweredFrequencyDropdown by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = showPoweredFrequencyDropdown,
                    onExpandedChange = { showPoweredFrequencyDropdown = it }
                ) {
                    OutlinedTextField(
                        value = "$sensorUpdateFrequencyPowered",
                        onValueChange = {},
                        label = { Text("On Charging Frequency") },
                        readOnly = true,
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = showBatteryFrequencyDropdown)
                        }
                    )
                    ExposedDropdownMenu(
                        expanded = showPoweredFrequencyDropdown,
                        onDismissRequest = { showPoweredFrequencyDropdown = false }
                    ) {
                        UpdateFrequencies.forEach {
                            DropdownMenuItem(onClick = {
                                onPoweredFrequencyChanged(it)
                                showPoweredFrequencyDropdown = false
                                // TODO: Possibly adjust battery frequency if powered frequency set lower,
                                //       i.e. more mins between updates, than powered frequency
                                //       Since updating more frequently when on battery makes little sense
                            }) {
                                Text("$it minutes")
                            }
                        }
                    }
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                InfoNotification(
                    infoString = R.string.sensor_update_notification,
                    channelId = sensorWorkerChannel,
                    buttonString = R.string.sensor_worker_notification_channel
                )
            }
        }
    }
}
