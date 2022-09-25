package io.homeassistant.companion.android.sensors

import android.content.Context
import androidx.core.graphics.blue
import androidx.core.graphics.green
import androidx.core.graphics.red
import com.google.android.material.color.DynamicColors
import io.homeassistant.companion.android.common.sensors.SensorManager
import io.homeassistant.companion.android.common.R as commonR

class DynamicColorSensorManager : SensorManager {
    companion object {
        private const val TAG = "DynamicColor"

        val accentColorSensor = SensorManager.BasicSensor(
            "accent_color",
            "sensor",
            commonR.string.sensor_name_accent_color_sensor,
            commonR.string.sensor_description_accent_color_sensor,
            "mdi:palette",
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC
        )
    }

    override fun docsLink(): String {
        return "https://companion.home-assistant.io/docs/core/sensors#dynamic-color-sensor"
    }
    override val enabledByDefault: Boolean
        get() = false
    override val name: Int
        get() = commonR.string.sensor_name_dynamic_color

    override fun getAvailableSensors(context: Context): List<SensorManager.BasicSensor> {
        return listOf(accentColorSensor)
    }

    override fun requiredPermissions(sensorId: String): Array<String> {
        return emptyArray()
    }

    override fun requestSensorUpdate(context: Context) {
        updateAccentColor(context)
    }

    override fun hasSensor(context: Context): Boolean {
        return DynamicColors.isDynamicColorAvailable()
    }

    private fun updateAccentColor(context: Context) {

        if (!isEnabled(context, accentColorSensor.id))
            return

        val dynamicColorContext = DynamicColors.wrapContextIfAvailable(context)
        val attrsToResolve = intArrayOf(
            android.R.attr.colorAccent, // 0
        )
        val test = dynamicColorContext.obtainStyledAttributes(attrsToResolve)
        val accent = test.getColor(0, 0)
        val accentHex = java.lang.String.format("#%06X", 0xFFFFFF and accent)
        test.recycle()

        onSensorUpdated(
            context,
            accentColorSensor,
            accentHex,
            accentColorSensor.statelessIcon,
            mapOf(
                "rgb_color" to listOf(accent.red, accent.green, accent.blue)
            )
        )
    }
}
