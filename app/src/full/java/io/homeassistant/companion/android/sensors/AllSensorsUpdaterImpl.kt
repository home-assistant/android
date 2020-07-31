package io.homeassistant.companion.android.sensors

import android.content.Context
import io.homeassistant.companion.android.domain.integration.IntegrationUseCase
import io.homeassistant.companion.android.util.PermissionManager

class AllSensorsUpdaterImpl(
    integrationUseCase: IntegrationUseCase,
    appContext: Context
) :
    AllSensorsUpdater(integrationUseCase, appContext) {

    override suspend fun getManagers(): List<SensorManager> {
        val sensorManagers = mutableListOf(
            BatterySensorManager(),
            NetworkSensorManager()
        )

        if (integrationUseCase.isBackgroundTrackingEnabled() && PermissionManager.checkLocationPermission(appContext)) {
            sensorManagers.add(GeocodeSensorManager())
        }

        return sensorManagers
    }
}
