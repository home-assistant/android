package io.homeassistant.companion.android.sensors

import android.Manifest
import android.content.Context
import android.content.Context.SENSOR_SERVICE
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager.SENSOR_DELAY_NORMAL
import android.os.Build
import io.homeassistant.companion.android.domain.integration.SensorRegistration
import kotlin.math.roundToInt

class StepsSensorManager : SensorManager, SensorEventListener {
    companion object {

        private const val TAG = "StepsSensor"
        private val stepsSensor = SensorManager.BasicSensor(
            "steps_sensor",
            "sensor",
            "Steps Sensor",
            unitOfMeasurement = "steps"
        )
        private var stepsReading: String = "unavailable"
        lateinit var mySensorManager: android.hardware.SensorManager
    }

    override val name: String
        get() = "Steps Sensors"

    override val availableSensors: List<SensorManager.BasicSensor>
        get() = listOf(stepsSensor)

    override fun requiredPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            arrayOf(
                Manifest.permission.ACTIVITY_RECOGNITION
            )
        } else {
            arrayOf()
        }
    }

    override fun getSensorData(
        context: Context,
        sensorId: String
    ): SensorRegistration<Any> {
        return when (sensorId) {
            stepsSensor.id -> getStepsSensor(context)
            else -> throw IllegalArgumentException("Unknown sensorId: $sensorId")
        }
    }

    private fun getStepsSensor(context: Context): SensorRegistration<Any> {

        if (checkPermission(context)) {
            mySensorManager =
                context.getSystemService(SENSOR_SERVICE) as android.hardware.SensorManager

            val stepsSensors = mySensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
            if (stepsSensors != null) {
                mySensorManager.registerListener(
                    this,
                    stepsSensors,
                    SENSOR_DELAY_NORMAL
                )
            }
        }
        val icon = "mdi:walk"

        return stepsSensor.toSensorRegistration(
            stepsReading,
            icon,
            mapOf()
        )
    }

    override fun onAccuracyChanged(p0: Sensor?, p1: Int) {
        // Nothing happening here but we are required to call onAccuracyChanged for sensor events
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event != null) {
            if (event.sensor.type == Sensor.TYPE_STEP_COUNTER) {
                stepsReading = event.values[0].roundToInt().toString()
            }
        }
        mySensorManager.unregisterListener(this)
    }
}
