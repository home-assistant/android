package io.homeassistant.companion.android.common.sensors

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager.SENSOR_DELAY_NORMAL
import androidx.core.content.getSystemService
import io.homeassistant.companion.android.common.R as commonR
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import timber.log.Timber

class LightSensorManager :
    SensorManager,
    SensorEventListener {
    companion object {
        private var isListenerRegistered = false
        private var listenerLastRegistered = 0
        private val lightSensor = SensorManager.BasicSensor(
            "light_sensor",
            "sensor",
            commonR.string.sensor_name_light,
            commonR.string.sensor_description_light_sensor,
            "mdi:brightness-5",
            deviceClass = "illuminance",
            unitOfMeasurement = "lx",
            stateClass = SensorManager.STATE_CLASS_MEASUREMENT,
        )
    }

    private val ioScope: CoroutineScope = CoroutineScope(Dispatchers.IO + Job())

    override fun docsLink(): String {
        return "https://companion.home-assistant.io/docs/core/sensors#light-sensor"
    }
    override val name: Int
        get() = commonR.string.sensor_name_light

    override suspend fun getAvailableSensors(context: Context): List<SensorManager.BasicSensor> {
        return listOf(lightSensor)
    }

    override fun requiredPermissions(context: Context, sensorId: String): Array<String> {
        return emptyArray()
    }

    override fun hasSensor(context: Context): Boolean {
        val packageManager: PackageManager = context.packageManager
        return packageManager.hasSystemFeature(PackageManager.FEATURE_SENSOR_LIGHT)
    }

    private lateinit var latestContext: Context
    private lateinit var mySensorManager: android.hardware.SensorManager

    override suspend fun requestSensorUpdate(context: Context) {
        latestContext = context
        updateLightSensor()
    }

    private suspend fun updateLightSensor() {
        if (!isEnabled(latestContext, lightSensor)) {
            return
        }

        val now = System.currentTimeMillis()
        if (listenerLastRegistered + SensorManager.SENSOR_LISTENER_TIMEOUT < now && isListenerRegistered) {
            Timber.d("Re-registering listener as it appears to be stuck")
            mySensorManager.unregisterListener(this)
            isListenerRegistered = false
        }
        mySensorManager = latestContext.getSystemService()!!

        val lightSensors = mySensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)
        if (lightSensors != null && !isListenerRegistered) {
            mySensorManager.registerListener(
                this,
                lightSensors,
                SENSOR_DELAY_NORMAL,
            )
            Timber.d("Light sensor listener registered")
            isListenerRegistered = true
            listenerLastRegistered = now.toInt()
        }
    }

    override fun onAccuracyChanged(p0: Sensor?, p1: Int) {
        // Nothing happening here but we are required to call onAccuracyChanged for sensor events
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_LIGHT) {
            ioScope.launch {
                onSensorUpdated(
                    latestContext,
                    lightSensor,
                    event.values[0].roundToInt().toString(),
                    lightSensor.statelessIcon,
                    mapOf(),
                )
            }
        }
        mySensorManager.unregisterListener(this)
        Timber.d("Light sensor listener unregistered")
        isListenerRegistered = false
    }
}
