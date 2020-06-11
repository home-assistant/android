package io.homeassistant.companion.android.sensor

import android.content.Context
import android.util.Log
import io.homeassistant.companion.android.domain.integration.IntegrationUseCase
import io.homeassistant.companion.android.util.extensions.PermissionManager

class SensorUpdateManager(
    private val appContext: Context,
    private val integrationUseCase: IntegrationUseCase
) : SensorUpdater {

    companion object {
        private const val TAG = "AllSensorsUpdaterImpl"
    }

    override suspend fun updateSensors(): Boolean {
        val sensorManagers = mutableListOf(
            BatterySensorManager(appContext),
            NetworkSensorManager(appContext)
        )

        if (integrationUseCase.isBackgroundTrackingEnabled() && PermissionManager.checkLocationPermissions(appContext)) {
            sensorManagers.add(GeocodeSensorManager(appContext))
        }

        registerSensors(sensorManagers)

        var success = false
        try {
            success = integrationUseCase.updateSensors(sensorManagers.flatMap { it.getSensors() })
        } catch (e: Exception) {
            Log.e(TAG, "Exception while updating sensors.", e)
        }

        // We failed to update a sensor, we should register all the sensors again.
        if (!success) {
            registerSensors(sensorManagers)
        }
        return success
    }

    private suspend fun registerSensors(sensorManagers: List<SensorManager>) {
        sensorManagers.flatMap { it.getSensorRegistrations() }.forEach { registration ->
            // I want to call this async but because of the way we need to store the
            // fact we have registered it we can't
            try {
                integrationUseCase.registerSensor(registration)
            } catch (e: Exception) {
                Log.e(TAG, "Issue registering sensor: ${registration.uniqueId}", e)
            }
        }
    }
}
