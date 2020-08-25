package io.homeassistant.companion.android.sensors

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.homeassistant.companion.android.domain.integration.SensorRegistration

class ActivitySensorManager : BroadcastReceiver(), SensorManager {

    companion object {

        internal const val TAG = "ActivitySM"

        const val ACTION_REQUEST_ACTIVITY_UPDATES =
            "io.homeassistant.companion.android.background.REQUEST_ACTIVITY_UPDATES"
        const val ACTION_UPDATE_ACTIVITY =
            "io.homeassistant.companion.android.background.UPDATE_ACTIVITY"

        fun restartActivityTracking(context: Context) {
            // Noop
        }

        private val activity = SensorManager.BasicSensor(
            "detected_activity",
            "sensor",
            "Detected Activity"
        )

        private var last_activity: String = "unknown"
    }

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

    override fun getSensorData(context: Context, sensorId: String): SensorRegistration<Any> {
        TODO("Not yet implemented")
    }
}
