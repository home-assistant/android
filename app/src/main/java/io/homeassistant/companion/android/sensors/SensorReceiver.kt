package io.homeassistant.companion.android.sensors

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.media.AudioManager
import android.os.PowerManager
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.BuildConfig
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.common.data.integration.SensorRegistration
import io.homeassistant.companion.android.common.sensors.BatterySensorManager
import io.homeassistant.companion.android.common.sensors.LastUpdateManager
import io.homeassistant.companion.android.common.sensors.SensorReceiverBase
import io.homeassistant.companion.android.database.AppDatabase
import java.util.Locale

@AndroidEntryPoint
class SensorReceiver : SensorReceiverBase() {

    override val tag: String
        get() = TAG

    companion object {
        const val TAG = "SensorReceiver"
        val MANAGERS = listOf(
            ActivitySensorManager(),
            AppSensorManager(),
            AudioSensorManager(),
            BatterySensorManager(),
            BluetoothSensorManager(),
            DNDSensorManager(),
            GeocodeSensorManager(),
            KeyguardSensorManager(),
            LastRebootSensorManager(),
            LastUpdateManager(),
            LightSensorManager(),
            LocationSensorManager(),
            MobileDataManager(),
            NetworkSensorManager(),
            NextAlarmManager(),
            NotificationSensorManager(),
            PhoneStateSensorManager(),
            PowerSensorManager(),
            PressureSensorManager(),
            ProximitySensorManager(),
            StepsSensorManager(),
            StorageSensorManager(),
            TimeZoneManager(),
            TrafficStatsManager()
        )

        const val ACTION_REQUEST_SENSORS_UPDATE =
            "io.homeassistant.companion.android.background.REQUEST_SENSORS_UPDATE"
    }

    // Suppress Lint because we only register for the receiver if the android version matches the intent
    @SuppressLint("InlinedApi")
    override val skippableActions = mapOf(
        "android.app.action.NEXT_ALARM_CLOCK_CHANGED" to NextAlarmManager.nextAlarm.id,
        "android.bluetooth.device.action.ACL_CONNECTED" to BluetoothSensorManager.bluetoothConnection.id,
        "android.bluetooth.device.action.ACL_DISCONNECTED" to BluetoothSensorManager.bluetoothConnection.id,
        BluetoothAdapter.ACTION_STATE_CHANGED to BluetoothSensorManager.bluetoothState.id,
        Intent.ACTION_SCREEN_OFF to PowerSensorManager.interactiveDevice.id,
        Intent.ACTION_SCREEN_ON to PowerSensorManager.interactiveDevice.id,
        PowerManager.ACTION_POWER_SAVE_MODE_CHANGED to PowerSensorManager.powerSave.id,
        PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED to PowerSensorManager.doze.id,
        NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED to DNDSensorManager.dndSensor.id,
        AudioManager.ACTION_MICROPHONE_MUTE_CHANGED to AudioSensorManager.micMuted.id,
        AudioManager.ACTION_SPEAKERPHONE_STATE_CHANGED to AudioSensorManager.speakerphoneState.id,
        AudioManager.RINGER_MODE_CHANGED_ACTION to AudioSensorManager.audioSensor.id
    )

    override suspend fun updateSensors(
        context: Context,
        integrationUseCase: IntegrationRepository
    ) {
        val sensorDao = AppDatabase.getInstance(context).sensorDao()
        val enabledRegistrations = mutableListOf<SensorRegistration<Any>>()

        val checkDeviceRegistration = integrationUseCase.getRegistration()
        if (checkDeviceRegistration.appVersion == null) {
            Log.w(TAG, "Device not registered, skipping sensor update/registration")
            return
        }

        val currentAppVersion = BuildConfig.VERSION_NAME
        val currentHaVersion = integrationUseCase.getHomeAssistantVersion()

        MANAGERS.forEach { manager ->

            // Since we don't have this manager injected it doesn't fulfil it's injects, manually
            // inject for now I guess?
            if (manager is LocationSensorManager)
                manager.integrationUseCase = integrationUseCase

            try {
                manager.requestSensorUpdate(context)
            } catch (e: Exception) {
                Log.e(TAG, "Issue requesting updates for ${context.getString(manager.name)}", e)
            }
            manager.getAvailableSensors(context).forEach { basicSensor ->
                val fullSensor = sensorDao.getFull(basicSensor.id)
                val sensor = fullSensor?.sensor
                val sensorCoreRegistration = sensor?.coreRegistration
                val sensorAppRegistration = sensor?.appRegistration

                // Always register enabled sensors in case of available entity updates
                // when app or core version change is detected every 4 hours
                if (sensor?.enabled == true && sensor.type.isNotBlank() && sensor.icon.isNotBlank() &&
                    (currentAppVersion != sensorAppRegistration || currentHaVersion != sensorCoreRegistration || !sensor.registered)
                ) {
                    val reg = fullSensor.toSensorRegistration()
                    val config = Configuration(context.resources.configuration)
                    config.setLocale(Locale("en"))
                    reg.name = context.createConfigurationContext(config).resources.getString(basicSensor.name)

                    try {
                        integrationUseCase.registerSensor(reg)
                        sensor.registered = true
                        sensor.coreRegistration = currentHaVersion
                        sensor.appRegistration = currentAppVersion
                        sensorDao.update(sensor)
                    } catch (e: Exception) {
                        Log.e(TAG, "Issue registering sensor: ${reg.uniqueId}", e)
                    }
                }
                if (sensor?.enabled == true && sensor.registered && sensor.state != sensor.lastSentState) {
                    enabledRegistrations.add(fullSensor.toSensorRegistration())
                }
            }
        }

        if (enabledRegistrations.isNotEmpty()) {
            var success = false
            try {
                success = integrationUseCase.updateSensors(enabledRegistrations.toTypedArray())
                enabledRegistrations.forEach {
                    sensorDao.updateLastSendState(it.uniqueId, it.state.toString())
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception while updating sensors.", e)
            }

            // We failed to update a sensor, we should re register next time
            if (!success) {
                enabledRegistrations.forEach {
                    val sensor = sensorDao.get(it.uniqueId)
                    if (sensor != null) {
                        sensor.registered = false
                        sensor.lastSentState = ""
                        sensorDao.update(sensor)
                    }
                }
            }
        } else Log.d(TAG, "Nothing to update")
    }
}
