package io.homeassistant.companion.android.sensors

import android.content.Context
import android.provider.Settings.Global
import android.util.Log
import io.homeassistant.companion.android.domain.integration.SensorRegistration

class DNDSensorManager : SensorManager {
    companion object {
        private const val TAG = "DNDSensor"

        private val dndSensor = SensorManager.BasicSensor(
            "dnd_sensor",
            "sensor",
            "Do Not Disturb Sensor"
        )
        var dndState = "unavailable"
    }

    override val name: String
        get() = "Do Not Disturb Sensor"

    override val availableSensors: List<SensorManager.BasicSensor>
        get() = listOf(dndSensor)

    override fun requiredPermissions(): Array<String> {
        return emptyArray()
    }

    override fun getSensorData(
        context: Context,
        sensorId: String
    ): SensorRegistration<Any> {
        return when (sensorId) {
            dndSensor.id -> getDNDState(context)
            else -> throw IllegalArgumentException("Unknown sensorId: $sensorId")
        }
    }

    private fun getDNDState(context: Context): SensorRegistration<Any> {

        try {
            dndState = when (Global.getInt(context.contentResolver, "zen_mode")) {
                0 -> "off"
                1 -> "priority_only"
                2 -> "total_silence"
                3 -> "alarms_only"
                else -> "unknown"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting the devices DND mode", e)
        }

        val icon = "mdi:do-not-disturb"

        return dndSensor.toSensorRegistration(
            dndState,
            icon,
            mapOf()
        )
    }
}
