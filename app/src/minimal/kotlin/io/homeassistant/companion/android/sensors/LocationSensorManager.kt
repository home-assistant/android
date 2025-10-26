package io.homeassistant.companion.android.sensors

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.sensors.SensorManager

class LocationSensorManager :
    BroadcastReceiver(),
    SensorManager {

    companion object {
        const val MINIMUM_ACCURACY = 200

        const val ACTION_REQUEST_LOCATION_UPDATES =
            "io.homeassistant.companion.android.background.REQUEST_UPDATES"
        const val ACTION_REQUEST_ACCURATE_LOCATION_UPDATE =
            "io.homeassistant.companion.android.background.REQUEST_ACCURATE_UPDATE"
        const val ACTION_PROCESS_LOCATION =
            "io.homeassistant.companion.android.background.PROCESS_UPDATES"
        const val ACTION_PROCESS_GEO =
            "io.homeassistant.companion.android.background.PROCESS_GEOFENCE"
        const val ACTION_FORCE_HIGH_ACCURACY =
            "io.homeassistant.companion.android.background.FORCE_HIGH_ACCURACY"

        val backgroundLocation = SensorManager.BasicSensor(
            "location_background",
            "",
            commonR.string.basic_sensor_name_location_background,
            commonR.string.sensor_description_location_background,
            "mdi:map-marker-multiple",
            updateType = SensorManager.BasicSensor.UpdateType.LOCATION,
        )
        val zoneLocation = SensorManager.BasicSensor(
            "zone_background",
            "",
            commonR.string.basic_sensor_name_location_zone,
            commonR.string.sensor_description_location_zone,
            "mdi:map-marker-radius",
            updateType = SensorManager.BasicSensor.UpdateType.LOCATION,
        )
        val singleAccurateLocation = SensorManager.BasicSensor(
            "accurate_location",
            "",
            commonR.string.basic_sensor_name_location_accurate,
            commonR.string.sensor_description_location_accurate,
            "mdi:crosshairs-gps",
            updateType = SensorManager.BasicSensor.UpdateType.LOCATION,
        )

        fun setHighAccuracyModeSetting(context: Context, enabled: Boolean) {}
        fun setHighAccuracyModeIntervalSetting(context: Context, updateInterval: Int) {}
    }

    override fun onReceive(context: Context, intent: Intent) {
        // Noop
    }

    override val name: Int
        get() = commonR.string.sensor_name_location

    override suspend fun getAvailableSensors(context: Context): List<SensorManager.BasicSensor> {
        return listOf()
    }

    override fun requiredPermissions(context: Context, sensorId: String): Array<String> {
        // Noop
        return emptyArray()
    }

    override suspend fun requestSensorUpdate(context: Context) {
        // Noop
    }
}
