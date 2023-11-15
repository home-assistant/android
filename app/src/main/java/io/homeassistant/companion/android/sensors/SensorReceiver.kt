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
import io.homeassistant.companion.android.settings.SettingsActivity

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
            ActivitySensorManager(),
            AndroidAutoSensorManager(),
            AndroidOsSensorManager(),
            AppSensorManager(),
            AudioSensorManager(),
            BatterySensorManager(),
            BluetoothSensorManager(),
            CarSensorManager(),
            DisplaySensorManager(),
            DNDSensorManager(),
            DynamicColorSensorManager(),
            DevicePolicyManager(),
            GeocodeSensorManager(),
            KeyguardSensorManager(),
            LastAppSensorManager(),
            LastRebootSensorManager(),
            LastUpdateManager(),
            LightSensorManager(),
            LocationSensorManager(),
            MobileDataManager(),
            NetworkSensorManager(),
            NfcSensorManager(),
            NextAlarmManager(),
            NotificationSensorManager(),
            PhoneStateSensorManager(),
            PowerSensorManager(),
            PressureSensorManager(),
            ProximitySensorManager(),
            QuestSensorManager(),
            StepsSensorManager(),
            StorageSensorManager(),
            TimeZoneManager(),
            TrafficStatsManager()
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
        "android.app.action.NEXT_ALARM_CLOCK_CHANGED" to NextAlarmManager.nextAlarm.id,
        "android.bluetooth.device.action.ACL_CONNECTED" to BluetoothSensorManager.bluetoothConnection.id,
        "android.bluetooth.device.action.ACL_DISCONNECTED" to BluetoothSensorManager.bluetoothConnection.id,
        "com.oculus.intent.action.MOUNT_STATE_CHANGED" to QuestSensorManager.headsetMounted.id,
        "android.net.wifi.WIFI_AP_STATE_CHANGED" to NetworkSensorManager.hotspotState.id,
        BluetoothAdapter.ACTION_STATE_CHANGED to BluetoothSensorManager.bluetoothState.id,
        Intent.ACTION_SCREEN_OFF to PowerSensorManager.interactiveDevice.id,
        Intent.ACTION_SCREEN_ON to PowerSensorManager.interactiveDevice.id,
        PowerManager.ACTION_POWER_SAVE_MODE_CHANGED to PowerSensorManager.powerSave.id,
        PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED to PowerSensorManager.doze.id,
        NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED to DNDSensorManager.dndSensor.id,
        AudioManager.ACTION_MICROPHONE_MUTE_CHANGED to AudioSensorManager.micMuted.id,
        AudioManager.ACTION_SPEAKERPHONE_STATE_CHANGED to AudioSensorManager.speakerphoneState.id,
        AudioManager.RINGER_MODE_CHANGED_ACTION to AudioSensorManager.audioSensor.id,
        Intent.ACTION_MANAGED_PROFILE_UNAVAILABLE to DevicePolicyManager.isWorkProfile.id,
        Intent.ACTION_MANAGED_PROFILE_AVAILABLE to DevicePolicyManager.isWorkProfile.id,
        WifiManager.WIFI_STATE_CHANGED_ACTION to NetworkSensorManager.wifiState.id,
        NfcAdapter.ACTION_ADAPTER_STATE_CHANGED to NfcSensorManager.nfcStateSensor.id
    )

    override fun getSensorSettingsIntent(
        context: Context,
        sensorId: String,
        sensorManagerId: String,
        notificationId: Int
    ): PendingIntent? {
        val intent = SettingsActivity.newInstance(context).apply {
            putExtra("fragment", "sensors/$sensorId")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
        }
        return PendingIntent.getActivity(context, notificationId, intent, PendingIntent.FLAG_IMMUTABLE)
    }
}
