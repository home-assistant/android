package io.homeassistant.companion.android.home.views

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.tooling.preview.devices.WearDevices
import io.homeassistant.companion.android.common.R
import io.homeassistant.companion.android.common.sensors.SensorManager
import io.homeassistant.companion.android.database.sensor.Sensor
import io.homeassistant.companion.android.theme.WearAppTheme
import io.homeassistant.companion.android.util.batterySensorManager
import io.homeassistant.companion.android.util.sensorList
import io.homeassistant.companion.android.views.ListHeader
import io.homeassistant.companion.android.views.ThemeLazyColumn

@Composable
fun SensorManagerUi(
    allSensors: List<Sensor>?,
    allAvailSensors: List<SensorManager.BasicSensor>?,
    sensorManager: SensorManager,
    onSensorClicked: (String, Boolean) -> Unit,
) {
    WearAppTheme {
        ThemeLazyColumn {
            item {
                ListHeader(id = sensorManager.name)
            }
            val currentSensors = allSensors?.filter { sensor ->
                allAvailSensors?.firstOrNull { availableSensor ->
                    sensor.id == availableSensor.id
                } != null
            }

            if (allAvailSensors?.isEmpty() == true) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(vertical = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        ListHeader(id = R.string.loading)
                        CircularProgressIndicator()
                    }
                }
            } else {
                allAvailSensors?.size?.let { int ->
                    items(int, { allAvailSensors[it].id }) { index ->
                        val basicSensor = allAvailSensors[index]
                        val sensor = currentSensors?.firstOrNull { sensor ->
                            sensor.id == basicSensor.id
                        }
                        SensorUi(
                            sensor = sensor,
                            manager = sensorManager,
                            basicSensor = basicSensor,
                        ) { sensorId, enabled -> onSensorClicked(sensorId, enabled) }
                    }
                }
            }
        }
    }
}

@Preview(device = WearDevices.LARGE_ROUND)
@Composable
private fun PreviewSensorManagerUI() {
    CompositionLocalProvider {
        SensorManagerUi(
            allSensors = listOf(),
            allAvailSensors = sensorList,
            sensorManager = batterySensorManager,
        ) { _, _ -> }
    }
}
