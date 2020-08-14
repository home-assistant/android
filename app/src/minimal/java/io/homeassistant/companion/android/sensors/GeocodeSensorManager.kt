package io.homeassistant.companion.android.sensors

import android.content.Context
import io.homeassistant.companion.android.domain.integration.SensorRegistration

class GeocodeSensorManager : SensorManager {
    override val name: String
        get() = "Geocode Sensors"
    override val availableSensors: List<SensorManager.BasicSensor>
        get() = listOf()

    override fun requiredPermissions(): Array<String> {
        return emptyArray()
    }

    override fun getSensorData(context: Context, sensorId: String): SensorRegistration<Any> {
        TODO("Not yet implemented")
    }
}
