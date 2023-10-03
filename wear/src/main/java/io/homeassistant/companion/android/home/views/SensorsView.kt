package io.homeassistant.companion.android.home.views

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material3.Text
import androidx.wear.tooling.preview.devices.WearDevices
import io.homeassistant.companion.android.common.sensors.SensorManager
import io.homeassistant.companion.android.sensors.SensorReceiver
import io.homeassistant.companion.android.theme.WearAppTheme
import io.homeassistant.companion.android.views.ListHeader
import io.homeassistant.companion.android.views.ThemeLazyColumn
import io.homeassistant.companion.android.common.R as commonR

@Composable
fun SensorsView(
    onClickSensorManager: (SensorManager) -> Unit
) {
    WearAppTheme {
        val sensorManagers = getSensorManagers()
        ThemeLazyColumn {
            item {
                ListHeader(id = commonR.string.sensors)
            }
            items(sensorManagers.size, { sensorManagers[it].name }) { index ->
                val manager = sensorManagers[index]
                Row {
                    Chip(
                        modifier = Modifier
                            .fillMaxWidth(),
                        colors = ChipDefaults.secondaryChipColors(),
                        label = {
                            Text(
                                text = stringResource(manager.name),
                                fontWeight = FontWeight.Bold
                            )
                        },
                        onClick = { onClickSensorManager(manager) }
                    )
                }
            }
        }
    }
}

@Composable
fun getSensorManagers(): List<SensorManager> {
    val context = LocalContext.current
    return SensorReceiver.MANAGERS.sortedBy { context.getString(it.name) }.filter { it.hasSensor(context) }
}

@Preview(device = WearDevices.LARGE_ROUND)
@Composable
private fun PreviewSensorsView() {
    CompositionLocalProvider {
        SensorsView {}
    }
}
