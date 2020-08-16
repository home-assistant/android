package io.homeassistant.companion.android.sensors

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import io.homeassistant.companion.android.common.dagger.GraphComponentAccessor
import io.homeassistant.companion.android.database.AppDatabase
import io.homeassistant.companion.android.domain.integration.IntegrationUseCase
import io.homeassistant.companion.android.domain.integration.SensorRegistration
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
            BatterySensorManager(),
            BluetoothSensorManager(),
            NetworkSensorManager(),
            GeocodeSensorManager(),
            LightSensorManager(),
            NextAlarmManager(),
            PhoneStateSensorManager(),
            StorageSensorManager()
        )
    }

    private val ioScope: CoroutineScope = CoroutineScope(Dispatchers.IO + Job())

    @Inject
    lateinit var integrationUseCase: IntegrationUseCase

    private val chargingActions = listOf(
        Intent.ACTION_BATTERY_LOW,
        Intent.ACTION_BATTERY_OKAY,
        Intent.ACTION_POWER_CONNECTED,
        Intent.ACTION_POWER_DISCONNECTED
    )

    override fun onReceive(context: Context, intent: Intent) {

        DaggerSensorComponent.builder()
            .appComponent((context.applicationContext as GraphComponentAccessor).appComponent)
            .build()
            .inject(this)

        LocationBroadcastReceiver.restartLocationTracking(context)

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

    suspend fun updateSensors(
        context: Context,
        integrationUseCase: IntegrationUseCase
    ) {
        val sensorDao = AppDatabase.getInstance(context).sensorDao()
        // When we update the sensors make sure to request an accurate location.
        val intent = Intent(context, LocationBroadcastReceiver::class.java)
        intent.action = LocationBroadcastReceiver.ACTION_REQUEST_ACCURATE_LOCATION_UPDATE
        context.sendBroadcast(intent)

        val enabledRegistrations = mutableListOf<SensorRegistration<Any>>()

        MANAGERS.forEach { manager ->
            manager.availableSensors.forEach { basicSensor ->
                // Only if we are enabled should we try to get values.
                val sensorData = manager.getEnabledSensorData(context, basicSensor.id)
                val sensor = sensorDao.get(basicSensor.id)

                // Register Sensors if needed
                if (sensorData != null && sensor?.registered == false) {
                    try {
                        integrationUseCase.registerSensor(sensorData)
                        sensor.registered = true
                    } catch (e: Exception) {
                        Log.e(TAG, "Issue registering sensor: ${sensorData.uniqueId}", e)
                    }
                }
                if (sensorData != null) {
                    enabledRegistrations.add(sensorData)
                }
            }
        }

        var success = false
        try {
            success = integrationUseCase.updateSensors(enabledRegistrations.toTypedArray())
        } catch (e: Exception) {
            Log.e(TAG, "Exception while updating sensors.", e)
        }

        // We failed to update a sensor, we should re register next time
        if (!success) {
            enabledRegistrations.forEach {
                val sensor = sensorDao.get(it.uniqueId)
                if (sensor != null) {
                    sensor.registered = false
                    sensorDao.update(sensor)
                }
            }
        }
    }
}
