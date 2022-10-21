package io.homeassistant.companion.android.sensors

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.os.PowerManager
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.BuildConfig
import io.homeassistant.companion.android.common.sensors.AppSensorManager
import io.homeassistant.companion.android.common.sensors.AudioSensorManager
import io.homeassistant.companion.android.common.sensors.BatterySensorManager
import io.homeassistant.companion.android.common.sensors.DNDSensorManager
import io.homeassistant.companion.android.common.sensors.LastUpdateManager
import io.homeassistant.companion.android.common.sensors.NetworkSensorManager
import io.homeassistant.companion.android.common.sensors.NextAlarmManager
import io.homeassistant.companion.android.common.sensors.PowerSensorManager
import io.homeassistant.companion.android.common.sensors.SensorManager
import io.homeassistant.companion.android.common.sensors.SensorReceiverBase
import io.homeassistant.companion.android.common.sensors.StepsSensorManager

@AndroidEntryPoint
class SensorReceiver : SensorReceiverBase() {

    override val tag: String
        get() = TAG

    override val currentAppVersion: String
        get() = BuildConfig.VERSION_NAME

    override val managers: List<SensorManager>
        get() = MANAGERS

    companion object {
        const val TAG = "SensorReceiver"
        val MANAGERS = listOf(
            AppSensorManager(),
            AudioSensorManager(),
            BatterySensorManager(),
            BedtimeModeSensorManager(),
            DNDSensorManager(),
            LastUpdateManager(),
            NetworkSensorManager(),
            NextAlarmManager(),
            OnBodySensorManager(),
            PowerSensorManager(),
            StepsSensorManager(),
            TheaterModeSensorManager()
        )

        const val ACTION_REQUEST_SENSORS_UPDATE =
            "io.homeassistant.companion.android.background.REQUEST_SENSORS_UPDATE"
    }

    // Suppress Lint because we only register for the receiver if the android version matches the intent
    @SuppressLint("InlinedApi")
    override val skippableActions = mapOf(
        WifiManager.WIFI_STATE_CHANGED_ACTION to NetworkSensorManager.wifiState.id,
        "android.app.action.NEXT_ALARM_CLOCK_CHANGED" to NextAlarmManager.nextAlarm.id,
        Intent.ACTION_SCREEN_OFF to PowerSensorManager.interactiveDevice.id,
        Intent.ACTION_SCREEN_ON to PowerSensorManager.interactiveDevice.id,
        PowerManager.ACTION_POWER_SAVE_MODE_CHANGED to PowerSensorManager.powerSave.id,
        PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED to PowerSensorManager.doze.id,
        NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED to DNDSensorManager.dndSensor.id,
        AudioManager.ACTION_MICROPHONE_MUTE_CHANGED to AudioSensorManager.micMuted.id,
        AudioManager.ACTION_SPEAKERPHONE_STATE_CHANGED to AudioSensorManager.speakerphoneState.id,
        AudioManager.RINGER_MODE_CHANGED_ACTION to AudioSensorManager.audioSensor.id,
    )

    override fun getSensorSettingsIntent(context: Context, id: String): Intent? = null
}
