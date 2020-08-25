package io.homeassistant.companion.android.sensors

import android.content.Context
import android.content.Context.SENSOR_SERVICE
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager.SENSOR_DELAY_NORMAL
import io.homeassistant.companion.android.domain.integration.SensorRegistration
import java.math.RoundingMode

class PressureSensorManager : SensorManager, SensorEventListener {
    companion object {

        private const val TAG = "PressureSensor"
        private val pressureSensor = SensorManager.BasicSensor(
            "pressure_sensor",
            "sensor",
            "Pressure Sensor",
            "pressure",
            "hPa"
        )
        private var pressureReading: String = "unavailable"
        lateinit var mySensorManager: android.hardware.SensorManager
    }

    override val name: String
        get() = "Pressure Sensors"

    override val availableSensors: List<SensorManager.BasicSensor>
        get() = listOf(pressureSensor)

    override fun requiredPermissions(): Array<String> {
        return emptyArray()
    }

    override fun getSensorData(
        context: Context,
        sensorId: String
    ): SensorRegistration<Any> {
        return when (sensorId) {
            pressureSensor.id -> getPressureSensor(context)
            else -> throw IllegalArgumentException("Unknown sensorId: $sensorId")
        }
    }

    private fun getPressureSensor(context: Context): SensorRegistration<Any> {

        mySensorManager = context.getSystemService(SENSOR_SERVICE) as android.hardware.SensorManager

        val pressureSensors = mySensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE)
        if (pressureSensors != null) {
            mySensorManager.registerListener(
                this,
                pressureSensors,
                SENSOR_DELAY_NORMAL)
        }

        val icon = "mdi:gauge"

        return pressureSensor.toSensorRegistration(
            pressureReading,
            icon,
            mapOf()
        )
    }

    override fun onAccuracyChanged(p0: Sensor?, p1: Int) {
        // Nothing happening here but we are required to call onAccuracyChanged for sensor events
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event != null) {
            if (event.sensor.type == Sensor.TYPE_PRESSURE) {
                pressureReading = event.values[0].toBigDecimal().setScale(1, RoundingMode.HALF_EVEN).toString()
            }
        }
        mySensorManager.unregisterListener(this)
    }
}
