package io.homeassistant.companion.android.common.sensors

import android.app.NotificationManager
import android.content.Context
import androidx.core.content.getSystemService
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.util.STATE_UNKNOWN
import io.homeassistant.companion.android.common.util.isAutomotive

class DNDSensorManager : SensorManager {
    companion object {
        val dndSensor = SensorManager.BasicSensor(
            "dnd_sensor",
            "sensor",
            commonR.string.sensor_name_dnd,
            commonR.string.sensor_description_dnd_sensor,
            "mdi:minus-circle",
            deviceClass = "enum",
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC,
            updateType = SensorManager.BasicSensor.UpdateType.INTENT,
        )
    }

    override fun docsLink(): String {
        return "https://companion.home-assistant.io/docs/core/sensors#do-not-disturb-sensor"
    }

    override val name: Int
        get() = commonR.string.sensor_name_dnd

    override suspend fun getAvailableSensors(context: Context): List<SensorManager.BasicSensor> {
        return listOf(dndSensor)
    }

    override fun requiredPermissions(context: Context, sensorId: String): Array<String> {
        return emptyArray()
    }

    override suspend fun requestSensorUpdate(context: Context) {
        updateDNDState(context)
    }

    override fun hasSensor(context: Context): Boolean {
        return !context.isAutomotive()
    }

    private suspend fun updateDNDState(context: Context) {
        if (!isEnabled(context, dndSensor)) {
            return
        }

        val notificationManager =
            context.getSystemService<NotificationManager>()

        val state = when (notificationManager?.currentInterruptionFilter) {
            NotificationManager.INTERRUPTION_FILTER_ALARMS -> "alarms_only"
            NotificationManager.INTERRUPTION_FILTER_ALL -> "off"
            NotificationManager.INTERRUPTION_FILTER_NONE -> "total_silence"
            NotificationManager.INTERRUPTION_FILTER_PRIORITY -> "priority_only"
            NotificationManager.INTERRUPTION_FILTER_UNKNOWN -> STATE_UNKNOWN
            else -> STATE_UNKNOWN
        }

        onSensorUpdated(
            context,
            dndSensor,
            state,
            if (state != "off") dndSensor.statelessIcon else "mdi:minus-circle-off",
            mapOf(
                "options" to listOf("alarms_only", "off", "priority_only", "total_silence"),
            ),
        )
    }
}
