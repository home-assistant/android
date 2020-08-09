package io.homeassistant.companion.android.sensors

import android.content.Context
import io.homeassistant.companion.android.domain.integration.IntegrationUseCase

class AllSensorsUpdaterImpl(
    integrationUseCase: IntegrationUseCase,
    appContext: Context
) :
    AllSensorsUpdater(integrationUseCase, appContext) {

    override suspend fun getManagers(): List<SensorManager> {
        return mutableListOf(
            BatterySensorManager(),
            NetworkSensorManager(),
            GeocodeSensorManager()
        )
    }
}
