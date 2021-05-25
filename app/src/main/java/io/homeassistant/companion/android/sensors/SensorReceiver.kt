package io.homeassistant.companion.android.sensors

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.media.AudioManager
import android.os.PowerManager
import android.util.Log
import io.homeassistant.companion.android.common.dagger.GraphComponentAccessor
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.common.data.integration.SensorRegistration
import io.homeassistant.companion.android.database.AppDatabase
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SensorReceiver : BroadcastReceiver() {

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

    private val ioScope: CoroutineScope = CoroutineScope(Dispatchers.IO + Job())

    @Inject
    lateinit var integrationUseCase: IntegrationRepository

    private val chargingActions = listOf(
        Intent.ACTION_BATTERY_LOW,
        Intent.ACTION_BATTERY_OKAY,
        Intent.ACTION_POWER_CONNECTED,
        Intent.ACTION_POWER_DISCONNECTED
    )

    // Suppress Lint because we only register for the receiver if the android version matches the intent
    @SuppressLint("InlinedApi")
    private val skippableActions = mapOf(
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

    override fun onReceive(context: Context, intent: Intent) {

        DaggerSensorComponent.builder()
            .appComponent((context.applicationContext as GraphComponentAccessor).appComponent)
            .build()
            .inject(this)

        if (skippableActions.containsKey(intent.action)) {
            val sensor = skippableActions[intent.action]
            if (!isSensorEnabled(context, sensor!!)) {
                Log.d(TAG, String.format
                    ("Sensor %s corresponding to received event %s is disabled, skipping sensors update", sensor, intent.action))
                return
            }
        }

        if (isSensorEnabled(context, LastUpdateManager.lastUpdate.id)) {
            LastUpdateManager().sendLastUpdate(context, intent.action)
            val sensorDao = AppDatabase.getInstance(context).sensorDao()
            val allSettings = sensorDao.getSettings(LastUpdateManager.lastUpdate.id)
            for (setting in allSettings) {
                if (setting.value != "" && intent.action == setting.value) {
                    val eventData = intent.extras?.keySet()?.map { it.toString() to intent.extras?.get(it).toString() }?.toMap()?.plus("intent" to intent.action.toString())
                        ?: mapOf("intent" to intent.action.toString())
                    Log.d(TAG, "Event data: $eventData")
                    ioScope.launch {
                        try {
                            integrationUseCase.fireEvent(
                                "android.intent_received",
                                eventData as Map<String, Any>
                            )
                            Log.d(TAG, "Event successfully sent to Home Assistant")
                        } catch (e: Exception) {
                            Log.e(TAG, "Unable to send event data to Home Assistant", e)
                        }
                    }
                }
            }
        }

        ioScope.launch {
            updateSensors(context, integrationUseCase)
            if (chargingActions.contains(intent.action)) {
                // Add a 5 second delay to perform another update so charging state updates completely.
                // This is necessary as the system needs a few seconds to verify the charger.
                delay(5000L)
                updateSensors(context, integrationUseCase)
            }
        }
    }

    private fun isSensorEnabled(context: Context, id: String): Boolean {
        return AppDatabase.getInstance(context).sensorDao().get(id)?.enabled == true
    }

    suspend fun updateSensors(
        context: Context,
        integrationUseCase: IntegrationRepository
    ) {
        val sensorDao = AppDatabase.getInstance(context).sensorDao()
        val enabledRegistrations = mutableListOf<SensorRegistration<Any>>()

        MANAGERS.forEach { manager ->
            try {
                manager.requestSensorUpdate(context)
            } catch (e: Exception) {
                Log.e(TAG, "Issue requesting updates for ${context.getString(manager.name)}", e)
            }
            manager.getAvailableSensors(context).forEach { basicSensor ->
                val fullSensor = sensorDao.getFull(basicSensor.id)
                val sensor = fullSensor?.sensor

                // Register Sensors if needed
                if (sensor?.enabled == true && !sensor.registered && !sensor.type.isBlank()) {
                    val reg = fullSensor.toSensorRegistration()
                    val config = Configuration(context.resources.configuration)
                    config.setLocale(Locale("en"))
                    reg.name = context.createConfigurationContext(config).resources.getString(basicSensor.name)

                    try {
                        integrationUseCase.registerSensor(reg)
                        sensor.registered = true
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
