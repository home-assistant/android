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
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val status: Int = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)

        if (level == -1 || scale == -1) {
            Log.e(TAG, "Issue getting battery level!")
            return null
        }

        val isCharging: Boolean = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

        val chargerType = when (intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)) {
            BatteryManager.BATTERY_PLUGGED_AC -> "AC"
            BatteryManager.BATTERY_PLUGGED_USB -> "USB"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
            else -> "N/A"
        }

        val percent = (level.toFloat() / scale.toFloat() * 100.0f).toInt()
        var batteryIcon = "mdi:battery"
        if (isCharging)
            batteryIcon += "-charging"
        if (chargerType == "Wireless")
            batteryIcon += "-wireless"

        val batteryStep = percent / 10
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
            mapOf(
                "is_charging" to isCharging,
                "charger_type" to chargerType
            )
        )
    }
}
