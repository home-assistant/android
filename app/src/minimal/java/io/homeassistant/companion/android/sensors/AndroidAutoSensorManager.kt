package io.shpro.companion.android.sensors

import android.content.Context
import io.shpro.companion.android.common.R as commonR
import io.shpro.companion.android.common.sensors.SensorManager

class AndroidAutoSensorManager : SensorManager {

    override val name: Int
        get() = commonR.string.sensor_name_android_auto

    override suspend fun getAvailableSensors(context: Context): List<SensorManager.BasicSensor> {
        return listOf()
    }

    override fun requiredPermissions(sensorId: String): Array<String> {
        return emptyArray()
    }

    override suspend fun requestSensorUpdate(context: Context) {
        // Noop
    }
}
