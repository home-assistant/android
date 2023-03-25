package io.homeassistant.companion.android.sensors

import android.content.Context
import io.homeassistant.companion.android.common.sensors.SensorManager
import io.homeassistant.companion.android.common.R as commonR

class GeocodeSensorManager : SensorManager {

    companion object {
        private const val TAG = "GeocodeSM"
        val geocodedLocation = SensorManager.BasicSensor(
            "geocoded_location",
            "sensor",
            commonR.string.basic_sensor_name_geolocation,
            commonR.string.sensor_description_geocoded_location,
            "mdi:map"
        )
    }

    override val name: Int
        get() = commonR.string.sensor_name_geolocation

    override suspend fun getAvailableSensors(context: Context): List<SensorManager.BasicSensor> {
        return listOf()
    }

    override fun requiredPermissions(sensorId: String): Array<String> {
        return emptyArray()
    }

    override fun requestSensorUpdate(context: Context) {
        // No op
    }
}
