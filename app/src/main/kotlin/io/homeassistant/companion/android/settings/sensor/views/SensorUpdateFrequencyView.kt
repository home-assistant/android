package io.homeassistant.companion.android.settings.sensor.views

import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Divider
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.homeassistant.companion.android.common.R
import io.homeassistant.companion.android.common.util.CHANNEL_SENSOR_WORKER
import io.homeassistant.companion.android.database.settings.SensorUpdateFrequencySetting
import io.homeassistant.companion.android.util.compose.InfoNotification
import io.homeassistant.companion.android.util.compose.RadioButtonRow
import io.homeassistant.companion.android.util.safeBottomPaddingValues

@Composable
fun SensorUpdateFrequencyView(
    sensorUpdateFrequency: SensorUpdateFrequencySetting,
    onSettingChanged: (SensorUpdateFrequencySetting) -> Unit,
) {
    val scrollState = rememberScrollState()
    Box(modifier = Modifier.verticalScroll(scrollState)) {
        Column(
            modifier = Modifier
                .padding(safeBottomPaddingValues(applyHorizontal = false))
                .padding(all = 16.dp),
        ) {
            Text(
                text = stringResource(R.string.sensor_update_frequency_description),
                modifier = Modifier.padding(bottom = 16.dp),
            )
            Divider()
            RadioButtonRow(
                text = stringResource(R.string.sensor_update_frequency_normal),
                selected = sensorUpdateFrequency == SensorUpdateFrequencySetting.NORMAL,
                onClick = { onSettingChanged(SensorUpdateFrequencySetting.NORMAL) },
            )
            RadioButtonRow(
                text = stringResource(R.string.sensor_update_frequency_fast_charging),
                selected = sensorUpdateFrequency == SensorUpdateFrequencySetting.FAST_WHILE_CHARGING,
                onClick = { onSettingChanged(SensorUpdateFrequencySetting.FAST_WHILE_CHARGING) },
            )
            RadioButtonRow(
                text = stringResource(R.string.sensor_update_frequency_fast_always),
                selected = sensorUpdateFrequency == SensorUpdateFrequencySetting.FAST_ALWAYS,
                onClick = { onSettingChanged(SensorUpdateFrequencySetting.FAST_ALWAYS) },
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                InfoNotification(
                    infoString = R.string.sensor_update_notification,
                    channelId = CHANNEL_SENSOR_WORKER,
                    buttonString = R.string.sensor_worker_notification_channel,
                )
            }
        }
    }
}
