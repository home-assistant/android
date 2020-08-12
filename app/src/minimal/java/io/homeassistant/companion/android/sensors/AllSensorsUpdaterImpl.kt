package io.homeassistant.companion.android.sensors

import android.content.Context
import io.homeassistant.companion.android.domain.integration.IntegrationUseCase
import io.homeassistant.companion.android.util.PermissionManager

class AllSensorsUpdaterImpl(
    integrationUseCase: IntegrationUseCase,
    appContext: Context
) : AllSensorsUpdater(integrationUseCase, appContext) {

    override suspend fun getManagers(): List<SensorManager> {
        val sensorManagers = mutableListOf(
            BatterySensorManager(),
            NetworkSensorManager(),
            NextAlarmManager()
        )

        if (integrationUseCase.isCallTrackingEnabled() && PermissionManager.checkPhoneStatePermission(appContext)) {
            sensorManagers.add(PhoneStateSensorManager())
        }

        return sensorManagers
    }
}
