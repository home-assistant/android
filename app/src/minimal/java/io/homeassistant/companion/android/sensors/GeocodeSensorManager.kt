package io.homeassistant.companion.android.sensors

import android.content.Context
import io.homeassistant.companion.android.R

class GeocodeSensorManager : SensorManager {

    companion object {
        private const val TAG = "GeocodeSM"
        val geocodedLocation = SensorManager.BasicSensor(
            "geocoded_location",
            "sensor",
            "Geocoded Location",
            R.string.sensor_description_geocoded_location
        )
    }

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
