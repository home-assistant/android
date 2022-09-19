package io.homeassistant.companion.android.sensors

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.annotation.RequiresApi
import io.homeassistant.companion.android.common.sensors.SensorManager
import io.homeassistant.companion.android.common.R as commonR

class SettingsSensorManager : SensorManager {
    companion object {
        private const val TAG = "ScreenBrightness"

        val screenBrightness = SensorManager.BasicSensor(
            "screen_brightness",
            "sensor",
            commonR.string.basic_sensor_name_screen_brightness,
            commonR.string.sensor_description_screen_brightness,
        )
    }

    override fun docsLink(): String {
        return "https://companion.home-assistant.io/docs/core/sensors#last-used-app-sensor"
    }

    override val enabledByDefault: Boolean
        get() = false
    override val name: Int
        get() = commonR.string.sensor_name_device_settings

    override fun getAvailableSensors(context: Context): List<SensorManager.BasicSensor> {
        return listOf(screenBrightness)
    }

    override fun hasSensor(context: Context): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
    }

    override fun requiredPermissions(sensorId: String): Array<String> {
        return emptyArray()
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP_MR1)
    override fun requestSensorUpdate(
        context: Context
    ) {
        updateScreenBrightness(context)
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP_MR1)
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

        val icon = if (auto) "mdi:brightness-auto" else "mdi:brightness-5"
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
