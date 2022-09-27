package io.homeassistant.companion.android.sensors

import android.content.Context
import android.provider.Settings
import android.util.Log
import io.homeassistant.companion.android.common.sensors.SensorManager
import io.homeassistant.companion.android.common.R as commonR

class DisplaySensorManager : SensorManager {
    companion object {
        private const val TAG = "ScreenBrightness"

        val screenBrightness = SensorManager.BasicSensor(
            "screen_brightness",
            "sensor",
            commonR.string.basic_sensor_name_screen_brightness,
            commonR.string.sensor_description_screen_brightness,
            statelessIcon = "mdi:brightness-6"
        )
    }

    override fun docsLink(): String {
        return "https://companion.home-assistant.io/docs/core/sensors#screen-brightness-sensor"
    }

    override val enabledByDefault: Boolean
        get() = false
    override val name: Int
        get() = commonR.string.sensor_name_display_sensors

    override fun getAvailableSensors(context: Context): List<SensorManager.BasicSensor> {
        return listOf(screenBrightness)
    }

    override fun requiredPermissions(sensorId: String): Array<String> {
        return emptyArray()
    }

    override fun requestSensorUpdate(
        context: Context
    ) {
        updateScreenBrightness(context)
    }

    private fun updateScreenBrightness(context: Context) {
        if (!isEnabled(context, screenBrightness.id))
            return

        var brightness = 0
        var auto = false

        try {
            brightness =
                Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
            auto = Settings.System.getInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE
            ) == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
        } catch (e: Exception) {
            Log.e(TAG, "Unable to get screen brightness settings", e)
        }

        val icon = if (auto) "mdi:brightness-auto" else screenBrightness.statelessIcon
        onSensorUpdated(
            context,
            screenBrightness,
            brightness,
            icon,
            mapOf(
                "automatic" to auto
            )
        )
    }
}
