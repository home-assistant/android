package io.homeassistant.companion.android.sensors

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager.SENSOR_DELAY_NORMAL
import android.hardware.SensorManager.SENSOR_STATUS_ACCURACY_HIGH
import android.hardware.SensorManager.SENSOR_STATUS_ACCURACY_LOW
import android.hardware.SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM
import android.hardware.SensorManager.SENSOR_STATUS_NO_CONTACT
import android.hardware.SensorManager.SENSOR_STATUS_UNRELIABLE
import android.util.Log
import androidx.core.content.getSystemService
import io.homeassistant.companion.android.common.sensors.SensorManager
import kotlin.math.roundToInt
import io.homeassistant.companion.android.common.R as commonR

class HeartRateSensorManager : SensorManager, SensorEventListener {
    companion object {

        private const val TAG = "HRSensor"
        private var isListenerRegistered = false
        private val skipAccuracy = listOf(
            SENSOR_STATUS_UNRELIABLE,
            SENSOR_STATUS_NO_CONTACT
        )
        private val heartRate = SensorManager.BasicSensor(
            "heart_rate",
            "sensor",
            commonR.string.sensor_name_heart_rate,
            commonR.string.sensor_description_heart_rate,
            "mdi:heart-pulse",
            unitOfMeasurement = "bpm",
            stateClass = SensorManager.STATE_CLASS_MEASUREMENT
        )
    }

    override fun docsLink(): String {
        return "https://companion.home-assistant.io/docs/wear-os/sensors"
    }
    override val enabledByDefault: Boolean
        get() = false

    override val name: Int
        get() = commonR.string.sensor_name_heart_rate

    override fun getAvailableSensors(context: Context): List<SensorManager.BasicSensor> {
        return listOf(heartRate)
    }

    override fun requiredPermissions(sensorId: String): Array<String> {
        return arrayOf(Manifest.permission.BODY_SENSORS)
    }

    override fun hasSensor(context: Context): Boolean {
        val packageManager: PackageManager = context.packageManager
        return packageManager.hasSystemFeature(PackageManager.FEATURE_SENSOR_HEART_RATE)
    }

    private lateinit var latestContext: Context
    private lateinit var mySensorManager: android.hardware.SensorManager

    override fun requestSensorUpdate(
        context: Context
    ) {
        latestContext = context
        updateHeartRate()
    }

    private fun updateHeartRate() {
        if (!isEnabled(latestContext, heartRate.id))
            return

        mySensorManager = latestContext.getSystemService()!!

        val heartRateSensor = mySensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE)
        if (heartRateSensor != null && !isListenerRegistered) {
            mySensorManager.registerListener(
                this,
                heartRateSensor,
                SENSOR_DELAY_NORMAL
            )
            Log.d(TAG, "Heart Rate sensor listener registered")
            isListenerRegistered = true
        }
    }

    override fun onAccuracyChanged(p0: Sensor?, p1: Int) {
        // Nothing happening here but we are required to call onAccuracyChanged for sensor events
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_HEART_RATE && event.accuracy !in skipAccuracy) {
            onSensorUpdated(
                latestContext,
                heartRate,
                event.values[0].roundToInt().toString(),
                heartRate.statelessIcon,
                mapOf(
                    "accuracy" to getAccuracy(event.accuracy)
                )
            )
        }
        mySensorManager.unregisterListener(this)
        Log.d(TAG, "Heart Rate sensor listener unregistered")
        isListenerRegistered = false
    }

    private fun getAccuracy(accuracy: Int): String {
        return when (accuracy) {
            SENSOR_STATUS_ACCURACY_HIGH -> "high"
            SENSOR_STATUS_ACCURACY_MEDIUM -> "medium"
            SENSOR_STATUS_ACCURACY_LOW -> "low"
            else -> "unknown"
        }
    }
}
