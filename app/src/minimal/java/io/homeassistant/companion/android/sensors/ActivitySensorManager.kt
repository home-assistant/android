package io.homeassistant.companion.android.sensors

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ActivitySensorManager : BroadcastReceiver(), SensorManager {

    override fun onReceive(context: Context, intent: Intent) {
        // Noop
    }

    override val name: String
        get() = "Activity Sensors"

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
