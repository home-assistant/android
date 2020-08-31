package io.homeassistant.companion.android.sensors

import android.content.Context
import io.homeassistant.companion.android.R

class GeocodeSensorManager : SensorManager {
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
