package io.homeassistant.companion.android.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager.SENSOR_DELAY_NORMAL
import androidx.core.content.getSystemService
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.sensors.SensorManager
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import timber.log.Timber

class OnBodySensorManager :
    SensorManager,
    SensorEventListener {
    companion object {
        private var isListenerRegistered = false
        private val onBodySensor = SensorManager.BasicSensor(
            "on_body",
            "binary_sensor",
            commonR.string.sensor_name_on_body,
            commonR.string.sensor_description_on_body,
            "mdi:account",
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC,
            updateType = SensorManager.BasicSensor.UpdateType.INTENT,
        )
    }

    private lateinit var latestContext: Context
    private lateinit var mySensorManager: android.hardware.SensorManager

    private val ioScope: CoroutineScope = CoroutineScope(Dispatchers.IO + Job())

    override fun docsLink(): String {
        return "https://companion.home-assistant.io/docs/wear-os/sensors"
    }

    override val name: Int
        get() = commonR.string.sensor_name_on_body

    override suspend fun getAvailableSensors(context: Context): List<SensorManager.BasicSensor> {
        return listOf(onBodySensor)
    }

    override fun requiredPermissions(context: Context, sensorId: String): Array<String> {
        return emptyArray()
    }

    override fun hasSensor(context: Context): Boolean {
        mySensorManager = context.getSystemService()!!
        return mySensorManager.getDefaultSensor(Sensor.TYPE_LOW_LATENCY_OFFBODY_DETECT, true) != null
    }

    override suspend fun requestSensorUpdate(context: Context) {
        latestContext = context
        updateOnBodySensor()
    }

    private suspend fun updateOnBodySensor() {
        if (!isEnabled(latestContext, onBodySensor)) {
            return
        }

        mySensorManager = latestContext.getSystemService()!!

        val onBodySensors = mySensorManager.getDefaultSensor(Sensor.TYPE_LOW_LATENCY_OFFBODY_DETECT, true)
        if (onBodySensors != null && !isListenerRegistered) {
            mySensorManager.registerListener(
                this,
                onBodySensors,
                SENSOR_DELAY_NORMAL,
            )
            Timber.d("On body sensor listener registered")
            isListenerRegistered = true
        }
    }

    override fun onAccuracyChanged(p0: Sensor?, p1: Int) {
        // Nothing happening here but we are required to call onAccuracyChanged for sensor events
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_LOW_LATENCY_OFFBODY_DETECT) {
            val state = event.values[0].roundToInt() != 0
            Timber.d("onbody state: $state and ${event.values[0]}")
            ioScope.launch {
                onSensorUpdated(
                    latestContext,
                    onBodySensor,
                    state,
                    if (state) onBodySensor.statelessIcon else "mdi:account-off",
                    mapOf(),
                )
            }
        }

        // Send update immediately
        SensorReceiver.updateAllSensors(latestContext)
    }
}
