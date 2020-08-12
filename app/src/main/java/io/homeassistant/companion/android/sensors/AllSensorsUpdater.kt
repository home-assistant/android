package io.homeassistant.companion.android.sensors

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.PermissionChecker
import io.homeassistant.companion.android.database.AppDatabase
import io.homeassistant.companion.android.database.sensor.Sensor
import io.homeassistant.companion.android.domain.integration.IntegrationUseCase
import io.homeassistant.companion.android.domain.integration.SensorRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

abstract class AllSensorsUpdater(
    internal val integrationUseCase: IntegrationUseCase,
    internal val appContext: Context
) : SensorUpdater {
    companion object {
        internal const val TAG = "AllSensorsUpdaterImpl"
    }

    private val ioScope: CoroutineScope = CoroutineScope(Dispatchers.IO + Job())
    private val sensorDao = AppDatabase.getInstance(appContext).sensorDao()

    abstract suspend fun getManagers(): List<SensorManager>

    override suspend fun updateSensors() {
        // When we update the sensors make sure to request an accurate location.
        val intent = Intent(appContext, LocationBroadcastReceiver::class.java)
        intent.action = LocationBroadcastReceiver.ACTION_REQUEST_ACCURATE_LOCATION_UPDATE
        appContext.sendBroadcast(intent)

        val sensorManagers = getManagers()
        val enabledRegistrations = mutableListOf<SensorRegistration<Any>>()

        ioScope.launch {
            sensorManagers.forEach { manager ->
                manager.getSensorRegistrations(appContext).forEach { registration ->
                    // Ensure dao is up to date
                    var sensor = sensorDao.get(registration.uniqueId)
                    var hasPermission = true
                    manager.requiredPermissions().forEach {
                        val permission = PermissionChecker.checkSelfPermission(appContext,it)
                        hasPermission = hasPermission &&  permission == PermissionChecker.PERMISSION_GRANTED
                    }
                    if (sensor == null) {
                        sensor = Sensor(registration.uniqueId, hasPermission, false,
                            registration.state.toString()
                        )
                        sensorDao.add(sensor)
                    } else {
                        sensor.enabled = sensor.enabled && hasPermission
                        sensor.state = registration.state.toString()
                    }

                    // Register Sensors
                    if(!sensor.registered){
                        try{
                            integrationUseCase.registerSensor(registration)
                            sensor.registered = true
                        } catch (e: Exception){
                            Log.e(TAG, "Issue registering sensor: ${registration.uniqueId}", e)
                        }
                    }
                    sensorDao.update(sensor)

                    if(sensor.enabled && sensor.registered){
                        enabledRegistrations.add(registration)
                    }
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
        if(!success){
            enabledRegistrations.forEach {
                val sensor = sensorDao.get(it.uniqueId)
                if(sensor != null){
                    sensor.registered = false
                    sensorDao.update(sensor)
                }
            }
        }

    }
}
