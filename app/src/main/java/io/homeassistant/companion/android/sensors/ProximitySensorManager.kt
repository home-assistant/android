package io.homeassistant.companion.android.sensors

import android.content.Context
import android.content.Context.SENSOR_SERVICE
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager.SENSOR_DELAY_NORMAL
import kotlin.math.roundToInt

class ProximitySensorManager : SensorManager, SensorEventListener {
    companion object {

        private const val TAG = "ProximitySensor"
        private val proximitySensor = SensorManager.BasicSensor(
            "proximity_sensor",
            "sensor",
            "Proximity Sensor"
        )
    }

    private lateinit var latestContext: Context
    private lateinit var mySensorManager: android.hardware.SensorManager
    private var maxRange: Int = 0

    override val name: String
        get() = "Proximity Sensors"

    override val availableSensors: List<SensorManager.BasicSensor>
        get() = listOf(proximitySensor)

    override fun requiredPermissions(): Array<String> {
        return emptyArray()
    }

    override fun requestSensorUpdate(context: Context) {
        latestContext = context
        updateProximitySensor(context)
    }

    private fun updateProximitySensor(context: Context) {
        if (!isEnabled(context, proximitySensor.id))
            return

        mySensorManager = context.getSystemService(SENSOR_SERVICE) as android.hardware.SensorManager

        val proximitySensors = mySensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)
        if (proximitySensors != null) {
            mySensorManager.registerListener(
                this,
                proximitySensors,
                SENSOR_DELAY_NORMAL
            )
            maxRange = proximitySensors.maximumRange.roundToInt()
        }
    }

    override fun onAccuracyChanged(p0: Sensor?, p1: Int) {
        // Nothing happening here but we are required to call onAccuracyChanged for sensor events
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event != null) {
            if (event.sensor.type == Sensor.TYPE_PROXIMITY) {
                val sensorValue = event.values[0].roundToInt()
                val state =
                    if (maxRange == 5 && sensorValue == 5)
                        "far"
                    else if (maxRange == 5)
                        "near"
                    else
                        sensorValue
                onSensorUpdated(
                    latestContext,
                    proximitySensor,
                    state,
                    "mdi:leak",
                    mapOf()
                )
            }
        }
        mySensorManager.unregisterListener(this)
    }
}
