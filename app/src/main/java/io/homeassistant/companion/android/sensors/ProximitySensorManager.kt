package io.homeassistant.companion.android.sensors

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager.SENSOR_DELAY_NORMAL
import android.util.Log
import androidx.core.content.getSystemService
import io.homeassistant.companion.android.common.sensors.SensorManager
import kotlin.math.roundToInt
import io.homeassistant.companion.android.common.R as commonR

class ProximitySensorManager : SensorManager, SensorEventListener {
    companion object {

        private const val TAG = "ProximitySensor"
        private var isListenerRegistered = false
        private val proximitySensor = SensorManager.BasicSensor(
            "proximity_sensor",
            "sensor",
            commonR.string.sensor_name_proximity,
            commonR.string.sensor_description_proximity_sensor,
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC
        )
    }

    private lateinit var latestContext: Context
    private lateinit var mySensorManager: android.hardware.SensorManager
    private var maxRange: Int = 0

    override fun docsLink(): String {
        return "https://companion.home-assistant.io/docs/core/sensors#proximity-sensor"
    }
    override val enabledByDefault: Boolean
        get() = false

    override val name: Int
        get() = commonR.string.sensor_name_proximity

    override fun getAvailableSensors(context: Context): List<SensorManager.BasicSensor> {
        return listOf(proximitySensor)
    }

    override fun requiredPermissions(sensorId: String): Array<String> {
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

        mySensorManager = latestContext.getSystemService()!!

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
        if (event?.sensor?.type == Sensor.TYPE_PROXIMITY) {
            val sensorValue = event.values[0].roundToInt()
            val state =
                when {
                    maxRange == 5 && sensorValue == 5 -> "far"
                    maxRange == 5 -> "near"
                    else -> sensorValue
                }
            onSensorUpdated(
                latestContext,
                proximitySensor,
                state,
                "mdi:leak",
                mapOf()
            )
        }
        mySensorManager.unregisterListener(this)
        Log.d(TAG, "Proximity sensor listener unregistered")
        isListenerRegistered = false
    }
}
