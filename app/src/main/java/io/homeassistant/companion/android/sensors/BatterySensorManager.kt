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

            getBatteryStateSensor(it)?.let { sensor ->
                retVal.add(
                    SensorRegistration(
                        sensor,
                        "Battery State",
                        "battery"
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

            getBatteryStateSensor(it)?.let { sensor ->
                retVal.add(sensor)
            }
        }

        return retVal
    }

    private fun getBatteryPercentage(level: Int, scale: Int): Int {
        return (level.toFloat() / scale.toFloat() * 100.0f).toInt()
    }

    private fun getBatteryIcon(percentage: Int, isCharging: Boolean = false, chargerType: String? = null, chargingStatus: String? = null): String {
        var batteryIcon = "mdi:battery"

        if (chargingStatus == "unknown") {
            batteryIcon += "-unknown"

            return batteryIcon
        }

        if (isCharging)
            batteryIcon += "-charging"

        if (chargerType == "wireless")
            batteryIcon += "-wireless"

        val batteryStep: Int = percentage / 10
        batteryIcon += when (batteryStep) {
            0 -> "-outline"
            10 -> ""
            else -> "-${batteryStep}0"
        }

        return batteryIcon
    }

    private fun getBatteryLevelSensor(intent: Intent): Sensor<Any>? {
        val level: Int = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale: Int = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)

        if (level == -1 || scale == -1) {
            Log.e(TAG, "Issue getting battery level!")
            return null
        }

        val percentage: Int = getBatteryPercentage(level, scale)

        return Sensor(
            "battery_level",
            percentage,
            "sensor",
            getBatteryIcon(percentage),
            mapOf()
        )
    }

    private fun getBatteryStateSensor(intent: Intent): Sensor<Any>? {
        val level: Int = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale: Int = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val status: Int = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)

        if (level == -1 || scale == -1 || status == -1) {
            Log.e(TAG, "Issue getting battery state!")
            return null
        }

        val isCharging: Boolean = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

        val chargerType: String = when (intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)) {
            BatteryManager.BATTERY_PLUGGED_AC -> "ac"
            BatteryManager.BATTERY_PLUGGED_USB -> "usb"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "wireless"
            else -> "unknown"
        }

        val chargingStatus: String = when (status) {
            BatteryManager.BATTERY_STATUS_FULL -> "full"
            BatteryManager.BATTERY_STATUS_CHARGING -> "charging"
            BatteryManager.BATTERY_STATUS_DISCHARGING -> "discharging"
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "not_charging"
            else -> "unknown"
        }

        val percentage: Int = getBatteryPercentage(level, scale)

        return Sensor(
            "battery_state",
            chargingStatus,
            "sensor",
            getBatteryIcon(percentage, isCharging, chargerType, chargingStatus),
            mapOf(
                "is_charging" to isCharging,
                "charger_type" to chargerType
            )
        )
    }
}
