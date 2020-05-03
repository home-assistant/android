package io.homeassistant.companion.android.sensor

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager.*
import android.util.Log
import io.homeassistant.companion.android.domain.integration.Sensor
import io.homeassistant.companion.android.domain.integration.SensorRegistration
import java.lang.StringBuilder

class BatterySensorManager(private val context: Context) : SensorManager {

    companion object {
        const val TAG = "BatterySensor"
        private val BATTERY_CHANGED_FILTER = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
    }

    override suspend fun getSensorRegistrations(): List<SensorRegistration<*>> {
        val batteryIntent = context.registerReceiver(null, BATTERY_CHANGED_FILTER) ?: return emptyList()
        val sensors = mutableListOf<SensorRegistration<*>>()
        val batteryLevelSensor = getBatteryLevelSensor(batteryIntent)
        if (batteryLevelSensor != null) {
            sensors.add(SensorRegistration(batteryLevelSensor, "Battery Level", "battery", "%"))
        }

        val batteryStateSensor = getBatteryStateSensor(batteryIntent)
        if (batteryStateSensor != null) {
            sensors.add(SensorRegistration(batteryStateSensor, "Battery State", "battery"))
        }
        return sensors
    }

    override suspend fun getSensors(): List<Sensor<*>> {
        val batteryIntent = context.registerReceiver(null, BATTERY_CHANGED_FILTER) ?: return emptyList()
        val sensors = mutableListOf<Sensor<*>>()
        val levelSensor = getBatteryLevelSensor(batteryIntent)
        if (levelSensor != null) {
            sensors.add(levelSensor)
        }

        val stateSensor = getBatteryStateSensor(batteryIntent)
        if (stateSensor != null) {
            sensors.add(stateSensor)
        }
        return sensors
    }

    private fun getBatteryPercentage(level: Int, scale: Int): Int {
        return (level.toFloat() / scale.toFloat() * 100.0f).toInt()
    }

    private fun getBatteryIcon(percentage: Int, isCharging: Boolean = false, chargerType: String? = null, chargingStatus: String? = null): String {
        val icon = StringBuilder("mdi:battery")
        if (chargingStatus == "unknown") {
            icon.append("-unknown")
            return icon.toString()
        }
        if (isCharging) {
            icon.append("-charging")
        }
        if (chargerType == "wireless") {
            icon.append("-wireless")
        }

        val batteryStep = percentage / 10
        icon.append(when (batteryStep) {
            0 -> "-outline"
            10 -> ""
            else -> "-${batteryStep}0"
        })
        return icon.toString()
    }

    private fun getBatteryLevelSensor(intent: Intent): Sensor<Int>? {
        val level: Int = intent.getIntExtra(EXTRA_LEVEL, -1)
        val scale: Int = intent.getIntExtra(EXTRA_SCALE, -1)

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

    private fun getBatteryStateSensor(intent: Intent): Sensor<String>? {
        val level: Int = intent.getIntExtra(EXTRA_LEVEL, -1)
        val scale: Int = intent.getIntExtra(EXTRA_SCALE, -1)
        val status: Int = intent.getIntExtra(EXTRA_STATUS, -1)

        if (level == -1 || scale == -1 || status == -1) {
            Log.e(TAG, "Issue getting battery state!")
            return null
        }

        val isCharging: Boolean = status == BATTERY_STATUS_CHARGING || status == BATTERY_STATUS_FULL

        val chargerType: String = when (intent.getIntExtra(EXTRA_PLUGGED, -1)) {
            BATTERY_PLUGGED_AC -> "ac"
            BATTERY_PLUGGED_USB -> "usb"
            BATTERY_PLUGGED_WIRELESS -> "wireless"
            else -> "unknown"
        }

        val chargingStatus: String = when (status) {
            BATTERY_STATUS_FULL -> "full"
            BATTERY_STATUS_CHARGING -> "charging"
            BATTERY_STATUS_DISCHARGING -> "discharging"
            BATTERY_STATUS_NOT_CHARGING -> "not_charging"
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
