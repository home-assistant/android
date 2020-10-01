package io.homeassistant.companion.android.sensors

import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import android.util.Log
import io.homeassistant.companion.android.common.dagger.GraphComponentAccessor
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.common.data.integration.SensorRegistration
import io.homeassistant.companion.android.database.AppDatabase
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
            AudioSensorManager(),
            BatterySensorManager(),
            BluetoothSensorManager(),
            DNDSensorManager(),
            GeocodeSensorManager(),
            LastRebootSensorManager(),
            LightSensorManager(),
            LocationSensorManager(),
            NetworkSensorManager(),
            NextAlarmManager(),
            PhoneStateSensorManager(),
            PowerSensorManager(),
            PressureSensorManager(),
            ProximitySensorManager(),
            StepsSensorManager(),
            StorageSensorManager(),
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

    private val skippableActions = mapOf(
        "android.app.action.NEXT_ALARM_CLOCK_CHANGED" to NextAlarmManager.nextAlarm.id,
        "android.bluetooth.device.action.ACL_CONNECTED" to BluetoothSensorManager.bluetoothConnection.id,
        "android.bluetooth.device.action.ACL_DISCONNECTED" to BluetoothSensorManager.bluetoothConnection.id,
        BluetoothAdapter.ACTION_STATE_CHANGED to BluetoothSensorManager.bluetoothState.id
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

        if (intent.action.equals(TelephonyManager.ACTION_PHONE_STATE_CHANGED) &&
            isSensorEnabled(context, PhoneStateSensorManager.callNumber.id)) {
            PhoneStateSensorManager().updateCallNumber(context, intent)
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
            manager.availableSensors.forEach { basicSensor ->
                val fullSensor = sensorDao.getFull(basicSensor.id)
                val sensor = fullSensor?.sensor

                // Register Sensors if needed
                if (sensor?.enabled == true && !sensor.registered && !sensor.type.isBlank()) {
                    val reg = fullSensor.toSensorRegistration()
                    reg.name = context.getString(basicSensor.name)
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
