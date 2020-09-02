package io.homeassistant.companion.android.sensors

import android.content.Context
import io.homeassistant.companion.android.R

class GeocodeSensorManager : SensorManager {

    companion object {
        private const val TAG = "GeocodeSM"
        val geocodedLocation = SensorManager.BasicSensor(
            "geocoded_location",
            "sensor",
            R.string.basic_sensor_name_geolocation,
            R.string.sensor_description_geocoded_location
        )
    }

    override val enabledByDefault: Boolean
        get() = false
    override val name: Int
        get() = R.string.sensor_name_geolocation

    override val availableSensors: List<SensorManager.BasicSensor>
        get() = listOf()

    override fun requiredPermissions(): Array<String> {
        return emptyArray()
    }

    override fun requestSensorUpdate(context: Context) {
        // No op
    }
}
