package io.homeassistant.companion.android.sensors

import android.content.Context
import android.provider.Settings.Global
import android.util.Log

class DNDSensorManager : SensorManager {
    companion object {
        private const val TAG = "DNDSensor"

        private val dndSensor = SensorManager.BasicSensor(
            "dnd_sensor",
            "sensor",
            "Do Not Disturb Sensor"
        )
    }

    override val name: String
        get() = "Do Not Disturb Sensor"

    override val availableSensors: List<SensorManager.BasicSensor>
        get() = listOf(dndSensor)

    override fun requiredPermissions(): Array<String> {
        return emptyArray()
    }

    override fun requestSensorUpdate(context: Context) {
        updateDNDState(context)
    }

    private fun updateDNDState(context: Context) {

        if (!isEnabled(context, dndSensor.id))
            return

        var dndState = "unavailable"
        try {
            dndState = when (Global.getInt(context.contentResolver, "zen_mode")) {
                0 -> "off"
                1 -> "priority_only"
                2 -> "total_silence"
                3 -> "alarms_only"
                else -> "unknown"
            }
            val icon = "mdi:do-not-disturb"

            onSensorUpdated(context,
                dndSensor,
                dndState,
                icon,
                mapOf()
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error getting the devices DND mode", e)
        }
    }
}
