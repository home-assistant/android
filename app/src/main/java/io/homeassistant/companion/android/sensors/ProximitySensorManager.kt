package io.homeassistant.companion.android.sensors

import android.content.Context
import android.content.Context.SENSOR_SERVICE
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager.SENSOR_DELAY_NORMAL
import io.homeassistant.companion.android.domain.integration.SensorRegistration
import kotlin.math.roundToInt

class ProximitySensorManager : SensorManager, SensorEventListener {
    companion object {

        private const val TAG = "ProximitySensor"
        private val proximitySensor = SensorManager.BasicSensor(
            "proximity_sensor",
            "sensor",
            "Proximity Sensor"
        )
        private var proximityReading: String = "unavailable"
        lateinit var mySensorManager: android.hardware.SensorManager
    }

    override val name: String
        get() = "Proximity Sensors"

    override val availableSensors: List<SensorManager.BasicSensor>
        get() = listOf(proximitySensor)

    override fun requiredPermissions(): Array<String> {
        return emptyArray()
    }

    override fun getSensorData(
        context: Context,
        sensorId: String
    ): SensorRegistration<Any> {
        return when (sensorId) {
            proximitySensor.id -> getProximitySensor(context)
            else -> throw IllegalArgumentException("Unknown sensorId: $sensorId")
        }
    }

    private fun getProximitySensor(context: Context): SensorRegistration<Any> {

        mySensorManager = context.getSystemService(SENSOR_SERVICE) as android.hardware.SensorManager

        val proximitySensors = mySensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)
        if (proximitySensors != null) {
            mySensorManager.registerListener(
                this,
                proximitySensors,
                SENSOR_DELAY_NORMAL)

            // Some devices only report 2 values, one of which is the max range so lets account for those devices
            if (proximitySensors.maximumRange.roundToInt() == 5) {
                proximityReading = if (proximityReading == "5") {
                    "far"
                } else {
                    "near"
                }
            }
        }

        val icon = "mdi:leak"

        return proximitySensor.toSensorRegistration(
            proximityReading,
            icon,
            mapOf()
        )
    }

    override fun onAccuracyChanged(p0: Sensor?, p1: Int) {
        // Nothing happening here but we are required to call onAccuracyChanged for sensor events
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event != null) {
            if (event.sensor.type == Sensor.TYPE_PROXIMITY) {
                proximityReading = event.values[0].roundToInt().toString()
            }
        }
        mySensorManager.unregisterListener(this)
    }
}
