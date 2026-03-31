package io.homeassistant.companion.android.sensors

import android.content.Context
import android.content.Intent
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.sensors.SensorManager

class WetModeSensorManager : SensorManager {
    companion object {
        val wetModeSensor = SensorManager.BasicSensor(
            "wet_mode",
            "binary_sensor",
            commonR.string.sensor_name_wet_mode,
            commonR.string.sensor_description_wet_mode,
            "mdi:water-off",
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC,
            updateType = SensorManager.BasicSensor.UpdateType.INTENT_ONLY,
        )
    }

    override fun docsLink(): String {
        return "https://companion.home-assistant.io/docs/wear-os/sensors"
    }

    private var wetModeEnabled: Boolean = false

    override val name: Int
        get() = commonR.string.sensor_name_wet_mode

    override suspend fun getAvailableSensors(context: Context): List<SensorManager.BasicSensor> {
        return listOf(
            wetModeSensor,
        )
    }

    override fun requiredPermissions(context: Context, sensorId: String): Array<String> {
        return emptyArray()
    }

    override suspend fun requestSensorUpdate(context: Context, intent: Intent?) {
        if (intent?.action == "com.google.android.clockwork.actions.WET_MODE_STARTED") {
            wetModeEnabled = true
        } else if (intent?.action == "com.google.android.clockwork.actions.WET_MODE_ENDED") {
            wetModeEnabled = false
        }

        updateWetMode(context)
    }

    override suspend fun requestSensorUpdate(context: Context) {
        // No Op
    }

    private suspend fun updateWetMode(context: Context) {
        if (!isEnabled(context, wetModeSensor)) {
            return
        }

        onSensorUpdated(
            context,
            wetModeSensor,
            wetModeEnabled,
            if (wetModeEnabled) "mdi:water" else wetModeSensor.statelessIcon,
            mapOf(),
        )
    }
}
