package io.homeassistant.companion.android.common.sensors

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager.SENSOR_DELAY_NORMAL
import android.os.Build
import androidx.core.content.getSystemService
import io.homeassistant.companion.android.common.R as commonR
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import timber.log.Timber

class StepsSensorManager :
    SensorManager,
    SensorEventListener {
    companion object {
        private var isListenerRegistered = false
        private var listenerLastRegistered = 0
        private val stepsSensor = SensorManager.BasicSensor(
            "steps_sensor",
            "sensor",
            commonR.string.sensor_name_steps,
            commonR.string.sensor_description_steps_sensor,
            "mdi:walk",
            unitOfMeasurement = "steps",
            stateClass = SensorManager.STATE_CLASS_TOTAL_INCREASING,
        )
    }

    private val ioScope: CoroutineScope = CoroutineScope(Dispatchers.IO + Job())

    override fun docsLink(): String {
        return "https://companion.home-assistant.io/docs/core/sensors#pedometer-sensors"
    }

    override val name: Int
        get() = commonR.string.sensor_name_steps

    override suspend fun getAvailableSensors(context: Context): List<SensorManager.BasicSensor> {
        return listOf(stepsSensor)
    }

    private lateinit var latestContext: Context
    private lateinit var mySensorManager: android.hardware.SensorManager

    override fun requiredPermissions(context: Context, sensorId: String): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            arrayOf(Manifest.permission.ACTIVITY_RECOGNITION)
        } else {
            emptyArray()
        }
    }

    override fun hasSensor(context: Context): Boolean {
        val packageManager: PackageManager = context.packageManager
        return packageManager.hasSystemFeature(PackageManager.FEATURE_SENSOR_STEP_COUNTER)
    }

    override suspend fun requestSensorUpdate(context: Context) {
        latestContext = context
        updateStepsSensor()
    }

    private suspend fun updateStepsSensor() {
        if (!isEnabled(latestContext, stepsSensor)) {
            return
        }

        if (checkPermission(latestContext, stepsSensor.id)) {
            val now = System.currentTimeMillis()
            if (listenerLastRegistered + SensorManager.SENSOR_LISTENER_TIMEOUT < now && isListenerRegistered) {
                Timber.d("Re-registering listener as it appears to be stuck")
                mySensorManager.unregisterListener(this)
                isListenerRegistered = false
            }
            mySensorManager = latestContext.getSystemService()!!

            val stepsSensors = mySensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
            if (stepsSensors != null && !isListenerRegistered) {
                mySensorManager.registerListener(
                    this,
                    stepsSensors,
                    SENSOR_DELAY_NORMAL,
                )
                Timber.d("Steps sensor listener registered")
                isListenerRegistered = true
                listenerLastRegistered = now.toInt()
            }
        }
    }

    override fun onAccuracyChanged(p0: Sensor?, p1: Int) {
        // Nothing happening here but we are required to call onAccuracyChanged for sensor events
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_STEP_COUNTER) {
            ioScope.launch {
                onSensorUpdated(
                    latestContext,
                    stepsSensor,
                    event.values[0].roundToInt().toString(),
                    stepsSensor.statelessIcon,
                    mapOf(),
                )
            }
        }
        mySensorManager.unregisterListener(this)
        Timber.d("Steps sensor listener unregistered")
        isListenerRegistered = false
    }
}
