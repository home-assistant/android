package io.homeassistant.companion.android.common.sensors

import android.content.Context
import android.provider.Settings
import android.util.Log
import io.homeassistant.companion.android.common.R as commonR

class DisplaySensorManager : SensorManager {
    companion object {
        private const val TAG = "ScreenBrightness"

        val screenBrightness = SensorManager.BasicSensor(
            "screen_brightness",
            "sensor",
            commonR.string.basic_sensor_name_screen_brightness,
            commonR.string.sensor_description_screen_brightness,
            statelessIcon = "mdi:brightness-6",
            docsLink = "https://companion.home-assistant.io/docs/core/sensors#screen-brightness-sensor"
        )

        val screenOffTimeout = SensorManager.BasicSensor(
            "screen_off_timeout",
            "sensor",
            commonR.string.sensor_name_screen_off_timeout,
            commonR.string.sensor_description_screen_off_timeout,
            "mdi:cellphone-off",
            docsLink = "https://companion.home-assistant.io/docs/core/sensors#screen-off-timeout-sensor"
        )
    }
    override val name: Int
        get() = commonR.string.sensor_name_display_sensors

    override suspend fun getAvailableSensors(context: Context): List<SensorManager.BasicSensor> {
        return listOf(screenBrightness, screenOffTimeout)
    }

    override fun requiredPermissions(sensorId: String): Array<String> {
        return emptyArray()
    }

    override fun requestSensorUpdate(
        context: Context
    ) {
        updateScreenBrightness(context)
        updateScreenTimeout(context)
    }

    private fun updateScreenBrightness(context: Context) {
        if (!isEnabled(context, screenBrightness)) {
            return
        }

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

    private fun updateScreenTimeout(context: Context) {
        if (!isEnabled(context, screenOffTimeout)) {
            return
        }

        var timeout = 0

        try {
            timeout =
                Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT)
        } catch (e: Exception) {
            Log.e(TAG, "Unable to get screen off timeout setting", e)
        }

        onSensorUpdated(
            context,
            screenOffTimeout,
            timeout,
            screenOffTimeout.statelessIcon,
            mapOf()
        )
    }
}
