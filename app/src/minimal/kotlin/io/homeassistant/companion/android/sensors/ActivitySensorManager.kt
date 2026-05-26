package io.homeassistant.companion.android.sensors

import android.content.Context
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.sensors.SensorManager

class ActivitySensorManager : SensorManager {

    override val name: Int
        get() = commonR.string.sensor_name_activity

    override suspend fun getAvailableSensors(context: Context): List<SensorManager.BasicSensor> {
        return listOf()
    }

    override fun requiredPermissions(context: Context, sensorId: String): Array<String> {
        return emptyArray()
    }

    override suspend fun requestSensorUpdate(context: Context) {
        // No-op: activity sensors require Google Play Services
    }
}
