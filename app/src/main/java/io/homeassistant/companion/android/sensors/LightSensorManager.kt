package io.homeassistant.companion.android.sensors

import android.content.Context
import android.content.Context.SENSOR_SERVICE
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager.SENSOR_DELAY_NORMAL
import android.util.Log
import io.homeassistant.companion.android.R
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
            "lx"
        )
    }

    override val enabledByDefault: Boolean
        get() = false

    override val name: Int
        get() = R.string.sensor_name_light

    override val availableSensors: List<SensorManager.BasicSensor>
        get() = listOf(lightSensor)

    override fun requiredPermissions(): Array<String> {
        return emptyArray()
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
                SENSOR_DELAY_NORMAL)
            Log.d(TAG, "Light sensor listener registered")
            isListenerRegistered = true
        }
    }

    override fun onAccuracyChanged(p0: Sensor?, p1: Int) {
        // Nothing happening here but we are required to call onAccuracyChanged for sensor events
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event != null) {
            if (event.sensor.type == Sensor.TYPE_LIGHT) {
                onSensorUpdated(
                    latestContext,
                    lightSensor,
                    event.values[0].roundToInt().toString(),
                    "mdi:brightness-5",
                    mapOf()
                )
            }
        }
        mySensorManager.unregisterListener(this)
        Log.d(TAG, "Light sensor listener unregistered")
        isListenerRegistered = false
    }
}
