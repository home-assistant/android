package io.homeassistant.companion.android.sensors

import android.content.Context
import android.content.Context.SENSOR_SERVICE
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager.SENSOR_DELAY_NORMAL
import android.util.Log
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.sensors.SensorManager
import kotlin.math.roundToInt

class LightSensorManager : SensorManager, SensorEventListener {
    companion object {

        private const val TAG = "LightSensor"
        private var isListenerRegistered = false
        private val lightSensor = SensorManager.BasicSensor(
            "light_sensor",
            "sensor",
            R.string.sensor_name_light,
            R.string.sensor_description_light_sensor,
            "illuminance",
            "lx",
            stateClass = SensorManager.STATE_CLASS_MEASUREMENT
        )
    }

    override fun docsLink(): String {
        return "https://companion.home-assistant.io/docs/core/sensors#light-sensor"
    }
    override val enabledByDefault: Boolean
        get() = false

    override val name: Int
        get() = R.string.sensor_name_light

    override fun getAvailableSensors(context: Context): List<SensorManager.BasicSensor> {
        return listOf(lightSensor)
    }

    override fun requiredPermissions(sensorId: String): Array<String> {
        return emptyArray()
    }

    override fun hasSensor(context: Context): Boolean {
        val packageManager: PackageManager = context.packageManager
        return packageManager.hasSystemFeature(PackageManager.FEATURE_SENSOR_LIGHT)
    }

    private lateinit var latestContext: Context
    private lateinit var mySensorManager: android.hardware.SensorManager

    override fun requestSensorUpdate(
        context: Context
    ) {
        latestContext = context
        updateLightSensor()
    }

    private fun updateLightSensor() {
        if (!isEnabled(latestContext, lightSensor.id))
            return

        mySensorManager = latestContext.getSystemService(SENSOR_SERVICE) as android.hardware.SensorManager

        val lightSensors = mySensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)
        if (lightSensors != null && !isListenerRegistered) {
            mySensorManager.registerListener(
                this,
                lightSensors,
                SENSOR_DELAY_NORMAL
            )
            Log.d(TAG, "Light sensor listener registered")
            isListenerRegistered = true
        }
    }

    override fun onAccuracyChanged(p0: Sensor?, p1: Int) {
        // Nothing happening here but we are required to call onAccuracyChanged for sensor events
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_LIGHT) {
            onSensorUpdated(
                latestContext,
                lightSensor,
                event.values[0].roundToInt().toString(),
                "mdi:brightness-5",
                mapOf()
            )
        }
        mySensorManager.unregisterListener(this)
        Log.d(TAG, "Light sensor listener unregistered")
        isListenerRegistered = false
    }
}
