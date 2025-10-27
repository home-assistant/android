package io.homeassistant.companion.android.common.sensors

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager.SENSOR_DELAY_NORMAL
import androidx.core.content.getSystemService
import io.homeassistant.companion.android.common.R as commonR
import java.math.RoundingMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import timber.log.Timber

class PressureSensorManager :
    SensorManager,
    SensorEventListener {
    companion object {
        private var isListenerRegistered = false
        private var listenerLastRegistered = 0
        private val pressureSensor = SensorManager.BasicSensor(
            "pressure_sensor",
            "sensor",
            commonR.string.sensor_name_pressure,
            commonR.string.sensor_description_pressure_sensor,
            "mdi:gauge",
            deviceClass = "atmospheric_pressure",
            unitOfMeasurement = "hPa",
            stateClass = SensorManager.STATE_CLASS_MEASUREMENT,
        )
    }

    private lateinit var latestContext: Context
    private lateinit var mySensorManager: android.hardware.SensorManager

    private val ioScope: CoroutineScope = CoroutineScope(Dispatchers.IO + Job())

    override fun docsLink(): String {
        return "https://companion.home-assistant.io/docs/core/sensors#pressure-sensor"
    }

    override val name: Int
        get() = commonR.string.sensor_name_pressure

    override suspend fun getAvailableSensors(context: Context): List<SensorManager.BasicSensor> {
        return listOf(pressureSensor)
    }

    override fun requiredPermissions(context: Context, sensorId: String): Array<String> {
        return emptyArray()
    }

    override suspend fun requestSensorUpdate(context: Context) {
        latestContext = context
        updatePressureSensor()
    }

    override fun hasSensor(context: Context): Boolean {
        val packageManager: PackageManager = context.packageManager
        return packageManager.hasSystemFeature(PackageManager.FEATURE_SENSOR_BAROMETER)
    }

    private suspend fun updatePressureSensor() {
        if (!isEnabled(latestContext, pressureSensor)) {
            return
        }

        val now = System.currentTimeMillis()
        if (listenerLastRegistered + SensorManager.SENSOR_LISTENER_TIMEOUT < now && isListenerRegistered) {
            Timber.d("Re-registering listener as it appears to be stuck")
            mySensorManager.unregisterListener(this)
            isListenerRegistered = false
        }

        mySensorManager = latestContext.getSystemService()!!

        val pressureSensors = mySensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE)
        if (pressureSensors != null && !isListenerRegistered) {
            mySensorManager.registerListener(
                this,
                pressureSensors,
                SENSOR_DELAY_NORMAL,
            )
            Timber.d("Pressure sensor listener registered")
            isListenerRegistered = true
            listenerLastRegistered = now.toInt()
        }
    }

    override fun onAccuracyChanged(p0: Sensor?, p1: Int) {
        // Nothing happening here but we are required to call onAccuracyChanged for sensor events
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event != null) {
            if (event.sensor.type == Sensor.TYPE_PRESSURE && !event.values[0].isNaN()) {
                ioScope.launch {
                    onSensorUpdated(
                        latestContext,
                        pressureSensor,
                        event.values[0].toBigDecimal().setScale(1, RoundingMode.HALF_EVEN).toString(),
                        pressureSensor.statelessIcon,
                        mapOf(),
                    )
                }
            }
        }
        mySensorManager.unregisterListener(this)
        Timber.d("Pressure sensor listener unregistered")
        isListenerRegistered = false
    }
}
