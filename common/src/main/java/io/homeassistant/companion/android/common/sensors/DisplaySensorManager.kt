package io.homeassistant.companion.android.common.sensors

import android.content.Context
import android.content.pm.PackageManager.FEATURE_SENSOR_ACCELEROMETER
import android.content.res.Configuration
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager.SENSOR_DELAY_NORMAL
import android.hardware.display.DisplayManager
import android.provider.Settings
import android.util.Log
import android.view.Display
import android.view.Surface
import androidx.core.content.getSystemService
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.util.STATE_UNKNOWN

class DisplaySensorManager : SensorManager, SensorEventListener {
    companion object {
        private const val TAG = "DisplaySensors"
        private var hasAccelerometer = false
        private var isListenerRegistered = false
        private var listenerLastRegistered = 0

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

        val screenOrientation = SensorManager.BasicSensor(
            "screen_orientation",
            "sensor",
            commonR.string.sensor_name_screen_orientation,
            commonR.string.sensor_description_screen_orientation,
            "mdi:screen-rotation",
            docsLink = "https://companion.home-assistant.io/docs/core/sensors#screen-orientation-sensor",
            updateType = SensorManager.BasicSensor.UpdateType.INTENT
        )

        val screenRotation = SensorManager.BasicSensor(
            "screen_rotation",
            "sensor",
            commonR.string.sensor_name_screen_rotation,
            commonR.string.sensor_description_screen_rotation,
            "mdi:screen-rotation",
            docsLink = "https://companion.home-assistant.io/docs/core/sensors#screen-rotation-sensor",
            unitOfMeasurement = "°"
        )

        val isFaceDown = SensorManager.BasicSensor(
            "is_face_down",
            "binary_sensor",
            commonR.string.sensor_name_is_face_down,
            commonR.string.sensor_description_is_face_down,
            "mdi:hand-pointing-down",
            docsLink = "https://companion.home-assistant.io/docs/core/sensors#is-face-down-sensor"
        )
    }
    override val name: Int
        get() = commonR.string.sensor_name_display_sensors

    override suspend fun getAvailableSensors(context: Context): List<SensorManager.BasicSensor> {
        hasAccelerometer = context.packageManager.hasSystemFeature(FEATURE_SENSOR_ACCELEROMETER)
        return if (hasAccelerometer) {
            listOf(screenBrightness, screenOffTimeout, screenOrientation, screenRotation, isFaceDown)
        } else {
            listOf(screenBrightness, screenOffTimeout, screenOrientation, screenRotation)
        }
    }

    override fun requiredPermissions(sensorId: String): Array<String> {
        return emptyArray()
    }

    override fun docsLink(): String {
        return "https://companion.home-assistant.io/docs/core/sensors#display-sensors"
    }

    private lateinit var latestContext: Context
    private lateinit var mySensorManager: android.hardware.SensorManager

    override fun requestSensorUpdate(
        context: Context
    ) {
        latestContext = context
        updateScreenBrightness(context)
        updateScreenTimeout(context)
        updateScreenOrientation(context)
        updateScreenRotation(context)
        if (hasAccelerometer) {
            updateIsFaceDown(context)
        }
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

    private fun updateScreenOrientation(context: Context) {
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
                "options" to listOf("portrait", "landscape", "square", STATE_UNKNOWN)
            )
        )
    }

    private fun updateScreenRotation(context: Context) {
        if (!isEnabled(context, screenRotation)) {
            return
        }

        val dm = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager

        val display = getRotationString(dm.getDisplay(Display.DEFAULT_DISPLAY).rotation)

        val hasMultipleDisplays = dm.displays.size > 1
        val multiple = dm.displays.associate { it.name to getRotationString(it.rotation) }

        onSensorUpdated(
            context,
            screenRotation,
            display,
            screenRotation.statelessIcon,
            if (hasMultipleDisplays) multiple else mapOf()
        )
    }

    private fun updateIsFaceDown(context: Context) {
        if (!isEnabled(context, isFaceDown)) {
            return
        }
        val now = System.currentTimeMillis()
        if (listenerLastRegistered + SensorManager.SENSOR_LISTENER_TIMEOUT < now && isListenerRegistered) {
            Log.d(TAG, "Re-registering listener as it appears to be stuck")
            mySensorManager.unregisterListener(this)
            isListenerRegistered = false
        }
        mySensorManager = latestContext.getSystemService()!!

        val accelerometerSensor = mySensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (accelerometerSensor != null && !isListenerRegistered) {
            mySensorManager.registerListener(
                this,
                accelerometerSensor,
                SENSOR_DELAY_NORMAL
            )
            Log.d(TAG, "Accelerometer sensor listener registered")
            isListenerRegistered = true
            listenerLastRegistered = now.toInt()
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_ACCELEROMETER) {
            // Following the example from: https://developer.android.com/reference/android/hardware/SensorEvent#values
            // When the device is lying flat with the screen down the value is inverted
            onSensorUpdated(
                latestContext,
                isFaceDown,
                event.values[2] < -9,
                isFaceDown.statelessIcon,
                mapOf()
            )
        }

        mySensorManager.unregisterListener(this)
        Log.d(TAG, "Accelerometer sensor listener unregistered")
        isListenerRegistered = false
    }

    override fun onAccuracyChanged(p0: Sensor?, p1: Int) {
        // No op
    }

    private fun getRotationString(rotate: Int): String = when (rotate) {
        Surface.ROTATION_0 -> "0"
        Surface.ROTATION_90 -> "90"
        Surface.ROTATION_180 -> "180"
        Surface.ROTATION_270 -> "270"
        else -> STATE_UNKNOWN
    }
}
