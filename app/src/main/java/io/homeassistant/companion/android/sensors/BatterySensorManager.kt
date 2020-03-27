package io.homeassistant.companion.android.sensors

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.util.Log
import io.homeassistant.companion.android.domain.integration.Sensor
import io.homeassistant.companion.android.domain.integration.SensorRegistration

class BatterySensorManager : SensorManager {

    companion object {
        const val TAG = "BatterySensor"
    }

    override fun getSensorRegistrations(context: Context): List<SensorRegistration<Any>> {
        return context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))?.let {
            val retVal = ArrayList<SensorRegistration<Any>>()

            getBatteryLevelSensor(it)?.let { sensor ->
                retVal.add(
                    SensorRegistration(
                        sensor,
                        "Battery Level",
                        "battery",
                        "%"
                    )
                )
            }

            return@let retVal
        } ?: listOf()
    }

    override fun getSensors(context: Context): List<Sensor<Any>> {
        val retVal = ArrayList<Sensor<Any>>()

        context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))?.let {
            getBatteryLevelSensor(it)?.let { sensor ->
                retVal.add(sensor)
            }
        }

        return retVal
    }

    private fun getBatteryLevelSensor(intent: Intent): Sensor<Any>? {
        val level: Int = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale: Int = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)

        if (level == -1 || scale == -1) {
            Log.e(TAG, "Issue getting battery level!")
            return null
        }

        val percent = (level.toFloat() / scale.toFloat() * 100.0f).toInt()
        val batteryStep = percent / 10

        var batteryIcon = "mdi:battery"
        batteryIcon += when (batteryStep) {
            0 -> "-outline"
            10 -> ""
            else -> "-${batteryStep}0"
        }

        return Sensor(
            "battery_level",
            percent,
            "sensor",
            batteryIcon,
            mapOf()
        )
    }
}
