package io.homeassistant.companion.android.sensors

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.homeassistant.companion.android.common.sensors.SensorManager
import io.homeassistant.companion.android.common.R as commonR

class ActivitySensorManager : BroadcastReceiver(), SensorManager {

    override fun onReceive(context: Context, intent: Intent) {
        // Noop
    }
    override val name: Int
        get() = commonR.string.sensor_name_activity

    override suspend fun getAvailableSensors(context: Context): List<SensorManager.BasicSensor> {
        return listOf()
    }

    override fun requiredPermissions(sensorId: String): Array<String> {
        // Noop
        return emptyArray()
    }

    override fun requestSensorUpdate(context: Context) {
        // Noop
    }
}
