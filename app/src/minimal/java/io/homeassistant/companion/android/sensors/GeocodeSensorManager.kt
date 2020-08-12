package io.homeassistant.companion.android.sensors

import android.content.Context
import io.homeassistant.companion.android.domain.integration.SensorRegistration

class GeocodeSensorManager : SensorManager {
    override fun requiredPermissions(): Array<String> {
        return emptyArray()
    }

    override fun getSensorRegistrations(context: Context): List<SensorRegistration<Any>> {
        return emptyList()
    }
}
