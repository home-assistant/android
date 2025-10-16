package io.homeassistant.companion.android.sensors

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.app.PendingIntent
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.nfc.NfcAdapter
import android.os.PowerManager
import androidx.core.app.TaskStackBuilder
import androidx.core.net.toUri
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.BuildConfig
import io.homeassistant.companion.android.common.sensors.AndroidOsSensorManager
import io.homeassistant.companion.android.common.sensors.AudioSensorManager
import io.homeassistant.companion.android.common.sensors.BatterySensorManager
import io.homeassistant.companion.android.common.sensors.BluetoothSensorManager
import io.homeassistant.companion.android.common.sensors.DNDSensorManager
import io.homeassistant.companion.android.common.sensors.DisplaySensorManager
import io.homeassistant.companion.android.common.sensors.KeyguardSensorManager
import io.homeassistant.companion.android.common.sensors.LastRebootSensorManager
import io.homeassistant.companion.android.common.sensors.LastUpdateManager
import io.homeassistant.companion.android.common.sensors.LightSensorManager
import io.homeassistant.companion.android.common.sensors.MobileDataManager
import io.homeassistant.companion.android.common.sensors.NetworkSensorManager
import io.homeassistant.companion.android.common.sensors.NextAlarmManager
import io.homeassistant.companion.android.common.sensors.NfcSensorManager
import io.homeassistant.companion.android.common.sensors.PhoneStateSensorManager
import io.homeassistant.companion.android.common.sensors.PowerSensorManager
import io.homeassistant.companion.android.common.sensors.PressureSensorManager
import io.homeassistant.companion.android.common.sensors.ProximitySensorManager
import io.homeassistant.companion.android.common.sensors.SensorManager
import io.homeassistant.companion.android.common.sensors.SensorReceiverBase
import io.homeassistant.companion.android.common.sensors.StepsSensorManager
import io.homeassistant.companion.android.common.sensors.StorageSensorManager
import io.homeassistant.companion.android.common.sensors.TimeZoneManager
import io.homeassistant.companion.android.common.sensors.TrafficStatsManager
import io.homeassistant.companion.android.home.HomeActivity
import io.homeassistant.companion.android.home.views.DEEPLINK_SENSOR_MANAGER

@AndroidEntryPoint
class SensorReceiver : SensorReceiverBase() {

    override val currentAppVersion: String
        get() = BuildConfig.VERSION_NAME

    override val managers: List<SensorManager>
        get() = MANAGERS

    companion object {
        val MANAGERS = listOf(
            AndroidOsSensorManager(),
            AppSensorManager(),
            AudioSensorManager(),
            BatterySensorManager(),
            BedtimeModeSensorManager(),
            BluetoothSensorManager(),
            DisplaySensorManager(),
            DNDSensorManager(),
            HealthServicesSensorManager(),
            HeartRateSensorManager(),
            KeyguardSensorManager(),
            LastRebootSensorManager(),
            LastUpdateManager(),
            LightSensorManager(),
            MobileDataManager(),
            NetworkSensorManager(),
            NextAlarmManager(),
            NfcSensorManager(),
            OnBodySensorManager(),
            PhoneStateSensorManager(),
            PowerSensorManager(),
            PressureSensorManager(),
            ProximitySensorManager(),
            StepsSensorManager(),
            StorageSensorManager(),
            TheaterModeSensorManager(),
            TimeZoneManager(),
            TrafficStatsManager(),
            WetModeSensorManager(),
        )

        const val ACTION_REQUEST_SENSORS_UPDATE =
            "io.homeassistant.companion.android.background.REQUEST_SENSORS_UPDATE"

        fun updateAllSensors(context: Context) {
            val intent = Intent(context, SensorReceiver::class.java)
            intent.action = ACTION_UPDATE_SENSORS
            context.sendBroadcast(intent)
        }
    }

    // Suppress Lint because we only register for the receiver if the android version matches the intent
    @SuppressLint("InlinedApi")
    override val skippableActions = mapOf(
        WifiManager.WIFI_STATE_CHANGED_ACTION to listOf(NetworkSensorManager.wifiState.id),
        "android.app.action.NEXT_ALARM_CLOCK_CHANGED" to listOf(NextAlarmManager.nextAlarm.id),
        Intent.ACTION_SCREEN_OFF to listOf(PowerSensorManager.interactiveDevice.id),
        Intent.ACTION_SCREEN_ON to listOf(PowerSensorManager.interactiveDevice.id),
        PowerManager.ACTION_POWER_SAVE_MODE_CHANGED to listOf(PowerSensorManager.powerSave.id),
        PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED to listOf(PowerSensorManager.doze.id),
        NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED to listOf(DNDSensorManager.dndSensor.id),
        AudioManager.ACTION_MICROPHONE_MUTE_CHANGED to listOf(AudioSensorManager.micMuted.id),
        AudioManager.ACTION_SPEAKERPHONE_STATE_CHANGED to listOf(AudioSensorManager.speakerphoneState.id),
        AudioManager.RINGER_MODE_CHANGED_ACTION to listOf(AudioSensorManager.audioSensor.id),
        AudioSensorManager.VOLUME_CHANGED_ACTION to listOf(
            AudioSensorManager.volAccessibility.id,
            AudioSensorManager.volAlarm.id,
            AudioSensorManager.volCall.id,
            AudioSensorManager.volDTMF.id,
            AudioSensorManager.volNotification.id,
            AudioSensorManager.volMusic.id,
            AudioSensorManager.volRing.id,
            AudioSensorManager.volSystem.id,
        ),
        "com.google.android.clockwork.actions.WET_MODE_STARTED" to listOf(WetModeSensorManager.wetModeSensor.id),
        "com.google.android.clockwork.actions.WET_MODE_ENDED" to listOf(WetModeSensorManager.wetModeSensor.id),
        "android.bluetooth.device.action.ACL_CONNECTED" to listOf(BluetoothSensorManager.bluetoothConnection.id),
        "android.bluetooth.device.action.ACL_DISCONNECTED" to listOf(BluetoothSensorManager.bluetoothConnection.id),
        BluetoothAdapter.ACTION_STATE_CHANGED to listOf(BluetoothSensorManager.bluetoothState.id),
        NfcAdapter.ACTION_ADAPTER_STATE_CHANGED to listOf(NfcSensorManager.nfcStateSensor.id),
    )

    override fun getSensorSettingsIntent(
        context: Context,
        sensorId: String,
        sensorManagerId: String,
        notificationId: Int,
    ): PendingIntent? {
        val intent = Intent(
            Intent.ACTION_VIEW,
            "$DEEPLINK_SENSOR_MANAGER/$sensorManagerId".toUri(),
            context,
            HomeActivity::class.java,
        )
        return TaskStackBuilder.create(context).run {
            addNextIntentWithParentStack(intent)
            getPendingIntent(notificationId, PendingIntent.FLAG_UPDATE_CURRENT)
        }
    }
}
