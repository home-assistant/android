package io.homeassistant.companion.android.sensors

import android.content.Context
import android.content.Context.SENSOR_SERVICE
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager.SENSOR_DELAY_NORMAL
import io.homeassistant.companion.android.domain.integration.SensorRegistration
import kotlin.math.roundToInt

class LightSensorManager : SensorManager, SensorEventListener {
    companion object {

        private const val TAG = "LightSensor"
        private val lightSensor = SensorManager.BasicSensor(
            "light_sensor",
            "sensor",
            "Light Sensor",
            "illuminance",
            "lx"
        )
        private var lightReading: String = "unavailable"
        lateinit var mySensorManager: android.hardware.SensorManager
    }

    override val name: String
        get() = "Light Sensors"

    override val availableSensors: List<SensorManager.BasicSensor>
        get() = listOf(lightSensor)

    override fun requiredPermissions(): Array<String> {
        return emptyArray()
    }

    override fun getSensorData(
        context: Context,
        sensorId: String
    ): SensorRegistration<Any> {
        return when (sensorId) {
            lightSensor.id -> getLightSensor(context)
            else -> throw IllegalArgumentException("Unknown sensorId: $sensorId")
        }
    }

    private fun getLightSensor(context: Context): SensorRegistration<Any> {

        mySensorManager = context.getSystemService(SENSOR_SERVICE) as android.hardware.SensorManager

        val lightSensors = mySensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)
        if (lightSensors != null) {
            mySensorManager.registerListener(
                this,
                lightSensors,
                SENSOR_DELAY_NORMAL)
        }

        val icon = "mdi:brightness-5"

        return lightSensor.toSensorRegistration(
            lightReading,
            icon,
            mapOf()
        )
    }

    override fun onAccuracyChanged(p0: Sensor?, p1: Int) {
        // Nothing happening here but we are required to call onAccuracyChanged for sensor events
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event != null) {
            if (event.sensor.type == Sensor.TYPE_LIGHT) {
                lightReading = event.values[0].roundToInt().toString()
            }
        }
        mySensorManager.unregisterListener(this)
    }
}
