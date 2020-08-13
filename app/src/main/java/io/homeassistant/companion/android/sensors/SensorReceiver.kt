package io.homeassistant.companion.android.sensors

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import io.homeassistant.companion.android.common.dagger.GraphComponentAccessor
import io.homeassistant.companion.android.database.AppDatabase
import io.homeassistant.companion.android.database.sensor.Sensor
import io.homeassistant.companion.android.domain.integration.IntegrationUseCase
import io.homeassistant.companion.android.domain.integration.SensorRegistration
import io.homeassistant.companion.android.util.PermissionManager
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class SensorReceiver : BroadcastReceiver() {

    companion object {
        const val TAG = "SensorReceiver"
        val MANAGERS = listOf(
            BatterySensorManager(),
            BluetoothSensorManager(),
            NetworkSensorManager(),
            GeocodeSensorManager(),
            NextAlarmManager(),
            PhoneStateSensorManager()
        )
    }

    private val ioScope: CoroutineScope = CoroutineScope(Dispatchers.IO + Job())

    @Inject
    lateinit var integrationUseCase: IntegrationUseCase

    override fun onReceive(context: Context, intent: Intent) {

        DaggerSensorComponent.builder()
            .appComponent((context as GraphComponentAccessor).appComponent)
            .build()
            .inject(this)

        ioScope.launch {
            updateSensors(context, integrationUseCase)
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
            manager.getSensorRegistrations(context).forEach { registration ->
                // Ensure dao is up to date
                var sensor = sensorDao.get(registration.uniqueId)
                var hasPermission = true
                manager.requiredPermissions().forEach {
                    hasPermission =
                        hasPermission && PermissionManager.hasPermission(context, it)
                }
                if (sensor == null) {
                    sensor = Sensor(
                        registration.uniqueId, hasPermission, false,
                        registration.state.toString()
                    )
                    sensorDao.add(sensor)
                } else {
                    sensor.enabled = sensor.enabled && hasPermission
                    sensor.state = registration.state.toString()
                }

                // Register Sensors
                if (!sensor.registered) {
                    try {
                        integrationUseCase.registerSensor(registration)
                        sensor.registered = true
                    } catch (e: Exception) {
                        Log.e(TAG, "Issue registering sensor: ${registration.uniqueId}", e)
                    }
                }
                sensorDao.update(sensor)

                if (sensor.enabled && sensor.registered) {
                    enabledRegistrations.add(registration)
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
