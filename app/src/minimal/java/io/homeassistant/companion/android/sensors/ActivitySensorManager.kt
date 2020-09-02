package io.homeassistant.companion.android.sensors

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.homeassistant.companion.android.R

class ActivitySensorManager : BroadcastReceiver(), SensorManager {

    override fun onReceive(context: Context, intent: Intent) {
        // Noop
    }

    override val enabledByDefault: Boolean
        get() = false
    override val name: Int
        get() = R.string.sensor_name_activity

    override val availableSensors: List<SensorManager.BasicSensor>
        get() = listOf()

    override fun requiredPermissions(): Array<String> {
        // Noop
        return emptyArray()
    }

    override fun requestSensorUpdate(context: Context) {
        // Noop
    }
}
