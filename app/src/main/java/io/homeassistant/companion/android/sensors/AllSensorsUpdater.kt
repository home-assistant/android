package io.homeassistant.companion.android.sensors

import android.content.Context
import android.content.Intent
import android.util.Log
import io.homeassistant.companion.android.SensorUpdater
import io.homeassistant.companion.android.background.LocationBroadcastReceiver
import io.homeassistant.companion.android.background.LocationBroadcastReceiverBase
import io.homeassistant.companion.android.domain.integration.IntegrationUseCase

abstract class AllSensorsUpdater(
    internal val integrationUseCase: IntegrationUseCase,
    internal val appContext: Context
) : SensorUpdater {
    companion object {
        internal const val TAG = "AllSensorsUpdaterImpl"
    }

    abstract suspend fun getManagers(): List<SensorManager>

    override suspend fun updateSensors() {
        // When we update the sensors make sure to request an accurate location.
        val intent = Intent(appContext, LocationBroadcastReceiver::class.java)
        intent.action = LocationBroadcastReceiverBase.ACTION_REQUEST_ACCURATE_LOCATION_UPDATE
        appContext.sendBroadcast(intent)

        val sensorManagers = getManagers()

        registerSensors(sensorManagers)

        var success = false
        try {
            success = integrationUseCase.updateSensors(
                sensorManagers.flatMap { it.getSensors(appContext) }.toTypedArray()
            )
        } catch (e: Exception) {
            Log.e(TAG, "Exception while updating sensors.", e)
        }

        // We failed to update a sensor, we should register all the sensors again.
        if (!success) {
            registerSensors(sensorManagers)
        }
    }

    private suspend fun registerSensors(sensorManagers: List<SensorManager>) {

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
