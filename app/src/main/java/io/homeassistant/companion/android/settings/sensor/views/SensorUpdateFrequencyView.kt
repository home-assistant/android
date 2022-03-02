package io.homeassistant.companion.android.settings.sensor.views

import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Divider
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.homeassistant.companion.android.common.R
import io.homeassistant.companion.android.common.sensors.BatterySensorManager
import io.homeassistant.companion.android.common.sensors.SensorWorkerBase
import io.homeassistant.companion.android.database.AppDatabase
import io.homeassistant.companion.android.database.settings.SensorUpdateFrequencySetting
import io.homeassistant.companion.android.util.compose.InfoNotification
import io.homeassistant.companion.android.util.compose.RadioButtonRow

@Composable
fun SensorUpdateFrequencyView(
    sensorUpdateFrequency: SensorUpdateFrequencySetting,
    onSettingChanged: (SensorUpdateFrequencySetting) -> Unit
) {
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .padding(20.dp)
            .verticalScroll(scrollState)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 20.dp)
        ) {
            Text(stringResource(R.string.sensor_update_frequency_description))
        }
        Divider()
        RadioButtonRow(
            text = stringResource(R.string.sensor_update_frequency_normal),
            selected = sensorUpdateFrequency == SensorUpdateFrequencySetting.NORMAL,
            onClick = { onSettingChanged(SensorUpdateFrequencySetting.NORMAL) }
        )
        val isCharging = AppDatabase.getInstance(context).sensorDao().get(BatterySensorManager.isChargingState.id)
        val sensorStatus = isCharging != null && isCharging.enabled
        RadioButtonRow(
            text = stringResource(
                when {
                    (sensorStatus) -> R.string.sensor_update_frequency_fast_charging
                    (sensorUpdateFrequency == SensorUpdateFrequencySetting.FAST_WHILE_CHARGING && !sensorStatus) ->
                        R.string.sensor_update_frequency_fast_charging_sensor_disabled_selected
                    (sensorUpdateFrequency != SensorUpdateFrequencySetting.FAST_WHILE_CHARGING && !sensorStatus) ->
                        R.string.sensor_update_frequency_fast_charging_sensor_disabled_unselected
                    else -> R.string.sensor_update_frequency_fast_charging
                }
            ),
            selected = sensorUpdateFrequency == SensorUpdateFrequencySetting.FAST_WHILE_CHARGING,
            onClick = {
                if (sensorStatus)
                    onSettingChanged(SensorUpdateFrequencySetting.FAST_WHILE_CHARGING)
            },
            enabled = sensorStatus
        )
        RadioButtonRow(
            text = stringResource(R.string.sensor_update_frequency_fast_always),
            selected = sensorUpdateFrequency == SensorUpdateFrequencySetting.FAST_ALWAYS,
            onClick = { onSettingChanged(SensorUpdateFrequencySetting.FAST_ALWAYS) }
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            InfoNotification(
                infoString = R.string.sensor_update_notification,
                channelId = SensorWorkerBase.channelId,
                buttonString = R.string.sensor_worker_notification_channel
            )
        }
    }
}
