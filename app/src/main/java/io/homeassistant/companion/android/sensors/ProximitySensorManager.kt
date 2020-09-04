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
import kotlin.math.roundToInt

class ProximitySensorManager : SensorManager, SensorEventListener {
    companion object {

        private const val TAG = "ProximitySensor"
        private var isListenerRegistered = false
        private val proximitySensor = SensorManager.BasicSensor(
            "proximity_sensor",
            "sensor",
            R.string.sensor_name_proximity,
            R.string.sensor_description_proximity_sensor
        )
    }

    private lateinit var latestContext: Context
    private lateinit var mySensorManager: android.hardware.SensorManager
    private var maxRange: Int = 0

    override val enabledByDefault: Boolean
        get() = false

    override val name: Int
        get() = R.string.sensor_name_proximity

    override val availableSensors: List<SensorManager.BasicSensor>
        get() = listOf(proximitySensor)

    override fun requiredPermissions(): Array<String> {
        return emptyArray()
    }

    override fun hasSensor(context: Context): Boolean {
        val packageManager: PackageManager = context.packageManager
        return packageManager.hasSystemFeature(PackageManager.FEATURE_SENSOR_PROXIMITY)
    }

    override fun requestSensorUpdate(context: Context) {
        latestContext = context
        updateProximitySensor()
    }

    private fun updateProximitySensor() {
        if (!isEnabled(latestContext, proximitySensor.id))
            return

        mySensorManager = latestContext.getSystemService(SENSOR_SERVICE) as android.hardware.SensorManager

        val proximitySensors = mySensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)
        if (proximitySensors != null && !isListenerRegistered) {
            mySensorManager.registerListener(
                this,
                proximitySensors,
                SENSOR_DELAY_NORMAL
            )
            Log.d(TAG, "Proximity sensor listener registered")
            isListenerRegistered = true
            maxRange = proximitySensors.maximumRange.roundToInt()
        }
    }

    override fun onAccuracyChanged(p0: Sensor?, p1: Int) {
        // Nothing happening here but we are required to call onAccuracyChanged for sensor events
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event != null) {
            if (event.sensor.type == Sensor.TYPE_PROXIMITY) {
                val sensorValue = event.values[0].roundToInt()
                val state =
                    if (maxRange == 5 && sensorValue == 5)
                        "far"
                    else if (maxRange == 5)
                        "near"
                    else
                        sensorValue
                onSensorUpdated(
                    latestContext,
                    proximitySensor,
                    state,
                    "mdi:leak",
                    mapOf()
                )
            }
        }
        mySensorManager.unregisterListener(this)
        Log.d(TAG, "Proximity sensor listener unregistered")
        isListenerRegistered = false
    }
}
