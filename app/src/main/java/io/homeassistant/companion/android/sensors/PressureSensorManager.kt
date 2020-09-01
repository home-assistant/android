package io.homeassistant.companion.android.sensors

import android.content.Context
import android.content.Context.SENSOR_SERVICE
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager.SENSOR_DELAY_NORMAL
import io.homeassistant.companion.android.R
import java.math.RoundingMode

class PressureSensorManager : SensorManager, SensorEventListener {
    companion object {

        private const val TAG = "PressureSensor"
        private val pressureSensor = SensorManager.BasicSensor(
            "pressure_sensor",
            "sensor",
            R.string.sensor_name_pressure,
            R.string.sensor_description_pressure_sensor,
            "pressure",
            "hPa"
        )
    }

    private lateinit var latestContext: Context
    private lateinit var mySensorManager: android.hardware.SensorManager

    override val name: Int
        get() = R.string.sensor_name_pressure

    override val availableSensors: List<SensorManager.BasicSensor>
        get() = listOf(pressureSensor)

    override fun requiredPermissions(): Array<String> {
        return emptyArray()
    }

    override fun requestSensorUpdate(context: Context) {
        latestContext = context
        updatePressureSensor(context)
    }

    private fun updatePressureSensor(context: Context) {
        if (!isEnabled(latestContext, pressureSensor.id))
            return

        mySensorManager = latestContext.getSystemService(SENSOR_SERVICE) as android.hardware.SensorManager

        val pressureSensors = mySensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE)
        if (pressureSensors != null) {
            mySensorManager.registerListener(
                this,
                pressureSensors,
                SENSOR_DELAY_NORMAL)
        }
    }

    override fun onAccuracyChanged(p0: Sensor?, p1: Int) {
        // Nothing happening here but we are required to call onAccuracyChanged for sensor events
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event != null) {
            if (event.sensor.type == Sensor.TYPE_PRESSURE) {
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
    }
}
