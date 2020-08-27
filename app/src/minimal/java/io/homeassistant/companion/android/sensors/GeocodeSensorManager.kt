package io.homeassistant.companion.android.sensors

import android.content.Context

class GeocodeSensorManager : SensorManager {
    override val name: String
        get() = "Geocode Sensors"
    override val availableSensors: List<SensorManager.BasicSensor>
        get() = listOf()

    override fun requiredPermissions(): Array<String> {
        return emptyArray()
    }

    override fun requestSensorUpdate(context: Context) {
        // No op
    }
}
