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
import android.os.Build
import androidx.core.content.getSystemService
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.sensors.SensorManager
import io.homeassistant.companion.android.common.util.STATE_UNKNOWN
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import timber.log.Timber

class HeartRateSensorManager :
    SensorManager,
    SensorEventListener {
    companion object {
        private var isListenerRegistered = false
        private var listenerLastRegistered = 0
        private val skipAccuracy = listOf(
            SENSOR_STATUS_UNRELIABLE,
            SENSOR_STATUS_NO_CONTACT,
        )
        private var eventCount = 0
        private val heartRate = SensorManager.BasicSensor(
            "heart_rate",
            "sensor",
            commonR.string.sensor_name_heart_rate,
            commonR.string.sensor_description_heart_rate,
            "mdi:heart-pulse",
            unitOfMeasurement = "bpm",
            stateClass = SensorManager.STATE_CLASS_MEASUREMENT,
        )
    }

    private val ioScope: CoroutineScope = CoroutineScope(Dispatchers.IO + Job())

    override fun docsLink(): String {
        return "https://companion.home-assistant.io/docs/wear-os/sensors"
    }

    override val name: Int
        get() = commonR.string.sensor_name_heart_rate

    override suspend fun getAvailableSensors(context: Context): List<SensorManager.BasicSensor> {
        return listOf(heartRate)
    }

    override fun requiredPermissions(context: Context, sensorId: String): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.BODY_SENSORS, Manifest.permission.BODY_SENSORS_BACKGROUND)
        } else {
            arrayOf(Manifest.permission.BODY_SENSORS)
        }
    }

    override fun hasSensor(context: Context): Boolean {
        val packageManager: PackageManager = context.packageManager
        return packageManager.hasSystemFeature(PackageManager.FEATURE_SENSOR_HEART_RATE)
    }

    private lateinit var latestContext: Context
    private lateinit var mySensorManager: android.hardware.SensorManager

    override suspend fun requestSensorUpdate(context: Context) {
        latestContext = context
        updateHeartRate()
    }

    private suspend fun updateHeartRate() {
        if (!isEnabled(latestContext, heartRate)) {
            return
        }

        val now = System.currentTimeMillis()
        if (listenerLastRegistered + 60000 < now && isListenerRegistered) {
            Timber.d("Re-registering listener as it appears to be stuck")
            mySensorManager.unregisterListener(this)
            isListenerRegistered = false
        }
        mySensorManager = latestContext.getSystemService()!!

        val heartRateSensor = mySensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE)
        if (heartRateSensor != null && !isListenerRegistered) {
            mySensorManager.registerListener(
                this,
                heartRateSensor,
                SENSOR_DELAY_NORMAL,
            )
            Timber.d("Heart Rate sensor listener registered")
            isListenerRegistered = true
            listenerLastRegistered = now.toInt()
        }
    }

    override fun onAccuracyChanged(p0: Sensor?, p1: Int) {
        // Nothing happening here but we are required to call onAccuracyChanged for sensor events
    }

    override fun onSensorChanged(event: SensorEvent?) {
        eventCount++
        val validReading = event?.sensor?.type == Sensor.TYPE_HEART_RATE &&
            event.accuracy !in skipAccuracy &&
            event.values[0].roundToInt() >= 0
        if (event?.sensor?.type == Sensor.TYPE_HEART_RATE) {
            Timber.d(
                "HR event received with accuracy: ${getAccuracy(
                    event.accuracy,
                )} and value: ${event.values[0]} with event count: $eventCount",
            )
        } else {
            Timber.d("No HR event received")
        }
        if (event != null && validReading) {
            ioScope.launch {
                onSensorUpdated(
                    latestContext,
                    heartRate,
                    event.values[0].roundToInt().toString(),
                    heartRate.statelessIcon,
                    mapOf(
                        "accuracy" to getAccuracy(event.accuracy),
                    ),
                )
            }
        }
        if (validReading || eventCount >= 10) {
            mySensorManager.unregisterListener(this)
            Timber.d("Heart Rate sensor listener unregistered")
            isListenerRegistered = false
            eventCount = 0
        }
    }

    private fun getAccuracy(accuracy: Int): String {
        return when (accuracy) {
            SENSOR_STATUS_ACCURACY_HIGH -> "high"
            SENSOR_STATUS_ACCURACY_MEDIUM -> "medium"
            SENSOR_STATUS_ACCURACY_LOW -> "low"
            SENSOR_STATUS_UNRELIABLE -> "unreliable"
            SENSOR_STATUS_NO_CONTACT -> "no_contact"
            else -> STATE_UNKNOWN
        }
    }
}
