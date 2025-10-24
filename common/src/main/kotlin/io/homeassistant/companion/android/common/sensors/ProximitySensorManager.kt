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

class ProximitySensorManager :
    SensorManager,
    SensorEventListener {
    companion object {
        private var isListenerRegistered = false
        private var listenerLastRegistered = 0
        private val proximitySensor = SensorManager.BasicSensor(
            "proximity_sensor",
            "sensor",
            commonR.string.sensor_name_proximity,
            commonR.string.sensor_description_proximity_sensor,
            "mdi:leak",
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC,
        )
    }

    private lateinit var latestContext: Context
    private lateinit var mySensorManager: android.hardware.SensorManager
    private var maxRange: Int = 0

    private val ioScope: CoroutineScope = CoroutineScope(Dispatchers.IO + Job())

    override fun docsLink(): String {
        return "https://companion.home-assistant.io/docs/core/sensors#proximity-sensor"
    }

    override val name: Int
        get() = commonR.string.sensor_name_proximity

    override suspend fun getAvailableSensors(context: Context): List<SensorManager.BasicSensor> {
        return listOf(proximitySensor)
    }

    override fun requiredPermissions(context: Context, sensorId: String): Array<String> {
        return emptyArray()
    }

    override fun hasSensor(context: Context): Boolean {
        val packageManager: PackageManager = context.packageManager
        return packageManager.hasSystemFeature(PackageManager.FEATURE_SENSOR_PROXIMITY)
    }

    override suspend fun requestSensorUpdate(context: Context) {
        latestContext = context
        updateProximitySensor()
    }

    private suspend fun updateProximitySensor() {
        if (!isEnabled(latestContext, proximitySensor)) {
            return
        }

        val now = System.currentTimeMillis()
        if (listenerLastRegistered + SensorManager.SENSOR_LISTENER_TIMEOUT < now && isListenerRegistered) {
            Timber.d("Re-registering listener as it appears to be stuck")
            mySensorManager.unregisterListener(this)
            isListenerRegistered = false
        }

        mySensorManager = latestContext.getSystemService()!!

        val proximitySensors = mySensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)
        if (proximitySensors != null && !isListenerRegistered) {
            mySensorManager.registerListener(
                this,
                proximitySensors,
                SENSOR_DELAY_NORMAL,
            )
            Timber.d("Proximity sensor listener registered")
            isListenerRegistered = true
            listenerLastRegistered = now.toInt()
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
            ioScope.launch {
                onSensorUpdated(
                    latestContext,
                    proximitySensor,
                    state,
                    proximitySensor.statelessIcon,
                    mapOf(),
                )
            }
        }
        mySensorManager.unregisterListener(this)
        Timber.d("Proximity sensor listener unregistered")
        isListenerRegistered = false
    }
}
