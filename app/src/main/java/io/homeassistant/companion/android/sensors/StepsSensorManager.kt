package io.homeassistant.companion.android.sensors

import android.Manifest
import android.content.Context
import android.content.Context.SENSOR_SERVICE
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager.SENSOR_DELAY_NORMAL
import android.os.Build
import io.homeassistant.companion.android.R
import kotlin.math.roundToInt

class StepsSensorManager : SensorManager, SensorEventListener {
    companion object {

        private const val TAG = "StepsSensor"
        private val stepsSensor = SensorManager.BasicSensor(
            "steps_sensor",
            "sensor",
            R.string.sensor_name_steps,
            R.string.sensor_description_steps_sensor,
            unitOfMeasurement = "steps"
        )
    }

    override val name: Int
        get() = R.string.sensor_name_steps

    override val availableSensors: List<SensorManager.BasicSensor>
        get() = listOf(stepsSensor)

    private lateinit var latestContext: Context
    private lateinit var mySensorManager: android.hardware.SensorManager

    override fun requiredPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            arrayOf(
                Manifest.permission.ACTIVITY_RECOGNITION
            )
        } else {
            arrayOf()
        }
    }

    override fun requestSensorUpdate(
        context: Context
    ) {
        latestContext = context
        updateStepsSensor()
    }

    private fun updateStepsSensor() {
        if (!isEnabled(latestContext, stepsSensor.id))
            return

        if (checkPermission(latestContext)) {
            mySensorManager =
                latestContext.getSystemService(SENSOR_SERVICE) as android.hardware.SensorManager

            val stepsSensors = mySensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
            if (stepsSensors != null) {
                mySensorManager.registerListener(
                    this,
                    stepsSensors,
                    SENSOR_DELAY_NORMAL
                )
            }
        }
    }

    override fun onAccuracyChanged(p0: Sensor?, p1: Int) {
        // Nothing happening here but we are required to call onAccuracyChanged for sensor events
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event != null) {
            if (event.sensor.type == Sensor.TYPE_STEP_COUNTER) {
                onSensorUpdated(
                    latestContext,
                    stepsSensor,
                    event.values[0].roundToInt().toString(),
                    "mdi:walk",
                    mapOf()
                )
            }
        }
        mySensorManager.unregisterListener(this)
    }
}
