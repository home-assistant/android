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
import java.math.RoundingMode
import io.homeassistant.companion.android.common.R as commonR

class PressureSensorManager : SensorManager, SensorEventListener {
    companion object {

        private const val TAG = "PressureSensor"
        private var isListenerRegistered = false
        private val pressureSensor = SensorManager.BasicSensor(
            "pressure_sensor",
            "sensor",
            commonR.string.sensor_name_pressure,
            commonR.string.sensor_description_pressure_sensor,
            "pressure",
            "hPa",
            stateClass = SensorManager.STATE_CLASS_MEASUREMENT
        )
    }

    private lateinit var latestContext: Context
    private lateinit var mySensorManager: android.hardware.SensorManager

    override fun docsLink(): String {
        return "https://companion.home-assistant.io/docs/core/sensors#pressure-sensor"
    }
    override val enabledByDefault: Boolean
        get() = false

    override val name: Int
        get() = commonR.string.sensor_name_pressure

    override fun getAvailableSensors(context: Context): List<SensorManager.BasicSensor> {
        return listOf(pressureSensor)
    }

    override fun requiredPermissions(sensorId: String): Array<String> {
        return emptyArray()
    }

    override fun requestSensorUpdate(context: Context) {
        latestContext = context
        updatePressureSensor()
    }

    override fun hasSensor(context: Context): Boolean {
        val packageManager: PackageManager = context.packageManager
        return packageManager.hasSystemFeature(PackageManager.FEATURE_SENSOR_BAROMETER)
    }

    private fun updatePressureSensor() {
        if (!isEnabled(latestContext, pressureSensor.id))
            return

        mySensorManager = latestContext.getSystemService()!!

        val pressureSensors = mySensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE)
        if (pressureSensors != null && !isListenerRegistered) {
            mySensorManager.registerListener(
                this,
                pressureSensors,
                SENSOR_DELAY_NORMAL
            )
            Log.d(TAG, "Pressure sensor listener registered")
            isListenerRegistered = true
        }
    }

    override fun onAccuracyChanged(p0: Sensor?, p1: Int) {
        // Nothing happening here but we are required to call onAccuracyChanged for sensor events
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event != null) {
            if (event.sensor.type == Sensor.TYPE_PRESSURE && !event.values[0].isNaN()) {
                onSensorUpdated(
                    latestContext,
                    pressureSensor,
                    event.values[0].toBigDecimal().setScale(1, RoundingMode.HALF_EVEN).toString(),
                    "mdi:gauge",
                    mapOf()
                )
            }
        }
        mySensorManager.unregisterListener(this)
        Log.d(TAG, "Pressure sensor listener unregistered")
        isListenerRegistered = false
    }
}
