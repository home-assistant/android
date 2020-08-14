package io.homeassistant.companion.android.sensors

import android.content.Context
import android.content.Context.SENSOR_SERVICE
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager.SENSOR_DELAY_NORMAL
import android.util.Log
import io.homeassistant.companion.android.domain.integration.SensorRegistration
import kotlin.math.roundToInt

class LightSensorManager : SensorManager, SensorEventListener {
    companion object {

        private const val TAG = "LightSensor"
        private var lightReading: String = 0f.roundToInt().toString()
        lateinit var mySensorManager: android.hardware.SensorManager
    }

    override val name: String
        get() = "Light Sensors"

    override fun requiredPermissions(): Array<String> {
        return emptyArray()
    }

    override fun getSensorRegistrations(context: Context): List<SensorRegistration<Any>> {
        return listOf(getLightSensor(context))
    }

    private fun getLightSensor(context: Context): SensorRegistration<Any> {

        mySensorManager = context.getSystemService(SENSOR_SERVICE) as android.hardware.SensorManager

        val lightSensor = mySensorManager.getDefaultSensor(Sensor.TYPE_LIGHT) as Sensor
        if(lightSensor != null){
            mySensorManager.registerListener(
                this,
                lightSensor,
                SENSOR_DELAY_NORMAL);

        } else {
            Log.e(TAG, "Light sensor not found for the device, sending unavailable")
            lightReading = "unavailable"
        }



        val icon = "mdi:brightness-5"

        return SensorRegistration(
            "light_sensor",
            lightReading,
            "sensor",
            icon,
            mapOf(),
            "Light Sensor",
            "illuminance",
            "lx"
        )
    }

    override fun onAccuracyChanged(p0: Sensor?, p1: Int) {
        // TODO Auto-generated method stub

    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event != null) {
            if(event.sensor.type == Sensor.TYPE_LIGHT){
                lightReading = event.values[0].roundToInt().toString()
            }
        }
    }

    fun onPause() {

        mySensorManager.unregisterListener(this)

       // super.onPause()
    }

}
