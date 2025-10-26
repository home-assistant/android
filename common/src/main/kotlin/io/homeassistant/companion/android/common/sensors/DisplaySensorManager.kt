package io.homeassistant.companion.android.common.sensors

import android.content.Context
import android.content.res.Configuration
import android.hardware.display.DisplayManager
import android.provider.Settings
import android.view.Display
import android.view.Surface
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.util.STATE_UNKNOWN
import timber.log.Timber

class DisplaySensorManager : SensorManager {
    companion object {
        val screenBrightness = SensorManager.BasicSensor(
            "screen_brightness",
            "sensor",
            commonR.string.basic_sensor_name_screen_brightness,
            commonR.string.sensor_description_screen_brightness,
            statelessIcon = "mdi:brightness-6",
            docsLink = "https://companion.home-assistant.io/docs/core/sensors#screen-brightness-sensor",
        )

        val screenOffTimeout = SensorManager.BasicSensor(
            "screen_off_timeout",
            "sensor",
            commonR.string.sensor_name_screen_off_timeout,
            commonR.string.sensor_description_screen_off_timeout,
            "mdi:cellphone-off",
            unitOfMeasurement = "ms",
            deviceClass = "duration",
            docsLink = "https://companion.home-assistant.io/docs/core/sensors#screen-off-timeout-sensor",
        )

        val screenOrientation = SensorManager.BasicSensor(
            "screen_orientation",
            "sensor",
            commonR.string.sensor_name_screen_orientation,
            commonR.string.sensor_description_screen_orientation,
            "mdi:screen-rotation",
            docsLink = "https://companion.home-assistant.io/docs/core/sensors#screen-orientation-sensor",
            updateType = SensorManager.BasicSensor.UpdateType.INTENT,
        )

        val screenRotation = SensorManager.BasicSensor(
            "screen_rotation",
            "sensor",
            commonR.string.sensor_name_screen_rotation,
            commonR.string.sensor_description_screen_rotation,
            "mdi:screen-rotation",
            docsLink = "https://companion.home-assistant.io/docs/core/sensors#screen-rotation-sensor",
            unitOfMeasurement = "°",
        )
    }

    override val name: Int
        get() = commonR.string.sensor_name_display_sensors

    override suspend fun getAvailableSensors(context: Context): List<SensorManager.BasicSensor> {
        return listOf(screenBrightness, screenOffTimeout, screenOrientation, screenRotation)
    }

    override fun requiredPermissions(context: Context, sensorId: String): Array<String> {
        return emptyArray()
    }

    override fun docsLink(): String {
        return "https://companion.home-assistant.io/docs/core/sensors#display-sensors"
    }

    override suspend fun requestSensorUpdate(context: Context) {
        updateScreenBrightness(context)
        updateScreenTimeout(context)
        updateScreenOrientation(context)
        updateScreenRotation(context)
    }

    private suspend fun updateScreenBrightness(context: Context) {
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
                Settings.System.SCREEN_BRIGHTNESS_MODE,
            ) == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
        } catch (e: Exception) {
            Timber.e(e, "Unable to get screen brightness settings")
        }

        val icon = if (auto) "mdi:brightness-auto" else screenBrightness.statelessIcon
        onSensorUpdated(
            context,
            screenBrightness,
            brightness,
            icon,
            mapOf(
                "automatic" to auto,
            ),
        )
    }

    private suspend fun updateScreenTimeout(context: Context) {
        if (!isEnabled(context, screenOffTimeout)) {
            return
        }

        var timeout = 0

        try {
            timeout =
                Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT)
        } catch (e: Exception) {
            Timber.e(e, "Unable to get screen off timeout setting")
        }

        onSensorUpdated(
            context,
            screenOffTimeout,
            timeout,
            screenOffTimeout.statelessIcon,
            mapOf(),
        )
    }

    private suspend fun updateScreenOrientation(context: Context) {
        if (!isEnabled(context, screenOrientation)) {
            return
        }

        @Suppress("DEPRECATION")
        val orientation = when (context.resources.configuration.orientation) {
            Configuration.ORIENTATION_PORTRAIT -> "portrait"
            Configuration.ORIENTATION_LANDSCAPE -> "landscape"
            Configuration.ORIENTATION_SQUARE -> "square"
            Configuration.ORIENTATION_UNDEFINED -> STATE_UNKNOWN
            else -> STATE_UNKNOWN
        }

        val icon = when (orientation) {
            "portrait" -> "mdi:phone-rotate-portrait"
            "landscape" -> "mdi:phone-rotate-landscape"
            "square" -> "mdi:crop-square"
            else -> screenOrientation.statelessIcon
        }

        onSensorUpdated(
            context,
            screenOrientation,
            orientation,
            icon,
            mapOf(
                "options" to listOf("portrait", "landscape", "square", STATE_UNKNOWN),
            ),
        )
    }

    private suspend fun updateScreenRotation(context: Context) {
        if (!isEnabled(context, screenRotation)) {
            return
        }

        val dm = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager

        val display = getRotationString(dm.getDisplay(Display.DEFAULT_DISPLAY).rotation)

        val multiple = dm.displays.associate { it.name to getRotationString(it.rotation) }
        val possibleStates = listOf("0", "90", "180", "270")
        val attrs = if (dm.displays.size > 1) {
            multiple.plus("options" to possibleStates)
        } else {
            mapOf("options" to possibleStates)
        }
        onSensorUpdated(
            context,
            screenRotation,
            display,
            screenRotation.statelessIcon,
            attrs,
        )
    }

    private fun getRotationString(rotate: Int): String = when (rotate) {
        Surface.ROTATION_0 -> "0"
        Surface.ROTATION_90 -> "90"
        Surface.ROTATION_180 -> "180"
        Surface.ROTATION_270 -> "270"
        else -> STATE_UNKNOWN
    }
}
