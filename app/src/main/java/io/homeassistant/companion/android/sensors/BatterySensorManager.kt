package io.homeassistant.companion.android.sensors

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import io.homeassistant.companion.android.R

class BatterySensorManager : SensorManager {

    companion object {
        private const val TAG = "BatterySensor"
        private val batteryLevel = SensorManager.BasicSensor(
            "battery_level",
            "sensor",
            R.string.basic_sensor_name_battery_level,
            R.string.sensor_description_battery_level,
            "battery",
            "%"
        )
        private val batteryState = SensorManager.BasicSensor(
            "battery_state",
            "sensor",
            R.string.basic_sensor_name_battery_state,
            R.string.sensor_description_battery_state
        )
        private val isChargingState = SensorManager.BasicSensor(
            "is_charging",
            "binary_sensor",
            R.string.basic_sensor_name_charging,
            R.string.sensor_description_charging,
            "plug"
        )
        private val chargerTypeState = SensorManager.BasicSensor(
            "charger_type",
            "sensor",
            R.string.basic_sensor_name_charger_type,
            R.string.sensor_description_charger_type
        )
        private val batteryHealthState = SensorManager.BasicSensor(
            "battery_health",
            "sensor",
            R.string.basic_sensor_name_battery_health,
            R.string.sensor_description_battery_health
        )

        private val batteryTemperature = SensorManager.BasicSensor(
            "battery_temperature",
            "sensor",
            R.string.basic_sensor_name_battery_temperature,
            R.string.sensor_description_battery_temperature,
            "temperature",
            "°C"
        )
    }

    override val enabledByDefault: Boolean
        get() = true

    override val name: Int
        get() = R.string.sensor_name_battery
    override val availableSensors: List<SensorManager.BasicSensor>
        get() = listOf(batteryLevel, batteryState, isChargingState, chargerTypeState, batteryHealthState, batteryTemperature)

    override fun requiredPermissions(sensorId: String): Array<String> {
        return emptyArray()
    }

    override fun requestSensorUpdate(
        context: Context
    ) {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        if (intent != null) {
            updateBatteryLevel(context, intent)
            updateBatteryState(context, intent)
            updateIsCharging(context, intent)
            updateChargerType(context, intent)
            updateBatteryHealth(context, intent)
            updateBatteryTemperature(context, intent)
        }
    }

    private fun getBatteryPercentage(intent: Intent): Int {
        val level: Int = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale: Int = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        return (level.toFloat() / scale.toFloat() * 100.0f).toInt()
    }

    private fun updateBatteryLevel(context: Context, intent: Intent) {
        if (!isEnabled(context, batteryLevel.id))
            return
        val percentage: Int = getBatteryPercentage(intent)

        var icon = "mdi:battery"

        val batteryStep: Int = percentage / 10
        icon += when (batteryStep) {
            0 -> "-outline"
            10 -> ""
            else -> "-${batteryStep}0"
        }

        onSensorUpdated(
            context,
            batteryLevel,
            percentage,
            icon,
            mapOf()
        )
    }

    private fun updateBatteryState(context: Context, intent: Intent) {
        if (!isEnabled(context, batteryState.id))
            return

        val chargingStatus = getChargingStatus(intent)

        val icon = when (chargingStatus) {
            "charging" -> "mdi:battery-plus"
            "discharging" -> "mdi:battery-minus"
            "full" -> "mdi:battery-charging"
            "not_charging" -> "mdi:battery"
            else -> "mdi:battery-unknown"
        }
        onSensorUpdated(
            context,
            batteryState,
            chargingStatus,
            icon,
            mapOf()
        )
    }

    private fun updateIsCharging(context: Context, intent: Intent) {
        if (!isEnabled(context, isChargingState.id))
            return

        val isCharging = getIsCharging(intent)

        val icon = if (isCharging) "mdi:power-plug" else "mdi:power-plug-off"
        onSensorUpdated(
            context,
            isChargingState,
            isCharging,
            icon,
            mapOf()
        )
    }

    private fun updateChargerType(context: Context, intent: Intent) {
        if (!isEnabled(context, chargerTypeState.id))
            return

        val chargerType = getChargerType(intent)

        val icon = when (chargerType) {
            "ac" -> "mdi:power-plug"
            "usb" -> "mdi:usb-port"
            "wireless" -> "mdi:battery-charging-wireless"
            else -> "mdi:battery"
        }
        onSensorUpdated(
            context,
            chargerTypeState,
            chargerType,
            icon,
            mapOf()
        )
    }

    private fun updateBatteryHealth(context: Context, intent: Intent) {
        if (!isEnabled(context, batteryHealthState.id))
            return

        val batteryHealth = getBatteryHealth(intent)

        val icon = when (batteryHealth) {
            "good" -> "mdi:battery-heart-variant"
            else -> "mdi:battery-alert"
        }
        onSensorUpdated(
            context,
            batteryHealthState,
            batteryHealth,
            icon,
            mapOf()
        )
    }

    private fun updateBatteryTemperature(context: Context, intent: Intent) {
        if (!isEnabled(context, batteryTemperature.id))
            return

        val batteryTemp = getBatteryTemperature(intent)

        onSensorUpdated(
                context,
                batteryTemperature,
                batteryTemp,
                "mdi:battery",
                mapOf()
        )
    }

    private fun getIsCharging(intent: Intent): Boolean {
        val status: Int = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)

        return status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
    }

    private fun getChargerType(intent: Intent): String {
        return when (intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)) {
            BatteryManager.BATTERY_PLUGGED_AC -> "ac"
            BatteryManager.BATTERY_PLUGGED_USB -> "usb"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "wireless"
            else -> "none"
        }
    }

    private fun getChargingStatus(intent: Intent): String {
        return when (intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)) {
            BatteryManager.BATTERY_STATUS_FULL -> "full"
            BatteryManager.BATTERY_STATUS_CHARGING -> "charging"
            BatteryManager.BATTERY_STATUS_DISCHARGING -> "discharging"
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "not_charging"
            else -> "unknown"
        }
    }

    private fun getBatteryHealth(intent: Intent): String {
        return when (intent.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)) {
            BatteryManager.BATTERY_HEALTH_COLD -> "cold"
            BatteryManager.BATTERY_HEALTH_DEAD -> "dead"
            BatteryManager.BATTERY_HEALTH_GOOD -> "good"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "overheated"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "over_voltage"
            BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "failed"
            else -> "unknown"
        }
    }

    private fun getBatteryTemperature(intent: Intent): Float {
        return intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10f
    }
}
