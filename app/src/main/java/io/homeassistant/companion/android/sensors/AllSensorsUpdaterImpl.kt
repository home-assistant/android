package io.homeassistant.companion.android.sensors

import android.content.Context
import android.util.Log
import io.homeassistant.companion.android.domain.integration.IntegrationUseCase
import io.homeassistant.companion.android.SensorUpdater

class AllSensorsUpdaterImpl(
    private val integrationUseCase: IntegrationUseCase,
    private val appContext: Context
) : SensorUpdater {
    companion object {
        private const val TAG = "AllSensorsUpdaterImpl"
    }

    override suspend fun updateSensors() {
        val sensorManagers = arrayOf(
            BatterySensorManager(),
            NetworkSensorManager(),
            GeocodeSensorManager()
        )

        registerSensors(sensorManagers)

        val success = integrationUseCase.updateSensors(
            sensorManagers.flatMap { it.getSensors(appContext) }.toTypedArray()
        )

        // We failed to update a sensor, we should register all the sensors again.
        if (!success) {
            registerSensors(sensorManagers)
        }
    }

    private suspend fun registerSensors(sensorManagers: Array<SensorManager>) {

        sensorManagers.flatMap {
            it.getSensorRegistrations(appContext)
        }.forEach {
            // I want to call this async but because of the way we need to store the
            // fact we have registered it we can't
            try {
                integrationUseCase.registerSensor(it)
            } catch (e: Exception) {
                Log.e(TAG, "Issue registering sensor: ${it.uniqueId}", e)
            }
        }
    }
}
