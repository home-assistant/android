package io.homeassistant.companion.android.common.sensors

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.util.STATE_UNAVAILABLE
import io.homeassistant.companion.android.common.util.STATE_UNKNOWN
import java.math.RoundingMode
import kotlin.math.floor
import timber.log.Timber

class BatterySensorManager : SensorManager {

    companion object {
        private const val SETTING_BATTERY_CURRENT_DIVISOR = "battery_current_divisor"
        private const val DEFAULT_BATTERY_CURRENT_DIVISOR = 1000000
        private const val SETTING_BATTERY_VOLTAGE_DIVISOR = "battery_voltage_divisor"
        private const val DEFAULT_BATTERY_VOLTAGE_DIVISOR = 1000
        private val batteryLevel = SensorManager.BasicSensor(
            "battery_level",
            "sensor",
            commonR.string.basic_sensor_name_battery_level,
            commonR.string.sensor_description_battery_level,
            "mdi:battery",
            deviceClass = "battery",
            unitOfMeasurement = "%",
            stateClass = SensorManager.STATE_CLASS_MEASUREMENT,
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC,
            enabledByDefault = true,
        )
        private val batteryState = SensorManager.BasicSensor(
            "battery_state",
            "sensor",
            commonR.string.basic_sensor_name_battery_state,
            commonR.string.sensor_description_battery_state,
            "mdi:battery-charging",
            deviceClass = "enum",
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC,
            updateType = SensorManager.BasicSensor.UpdateType.INTENT,
            enabledByDefault = true,
        )
        val isChargingState = SensorManager.BasicSensor(
            "is_charging",
            "binary_sensor",
            commonR.string.basic_sensor_name_charging,
            commonR.string.sensor_description_charging,
            "mdi:power-plug",
            deviceClass = "plug",
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC,
            updateType = SensorManager.BasicSensor.UpdateType.INTENT,
        )
        private val chargerTypeState = SensorManager.BasicSensor(
            "charger_type",
            "sensor",
            commonR.string.basic_sensor_name_charger_type,
            commonR.string.sensor_description_charger_type,
            "mdi:power-plug",
            deviceClass = "enum",
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC,
            updateType = SensorManager.BasicSensor.UpdateType.INTENT,
            enabledByDefault = true,
        )
        private val batteryHealthState = SensorManager.BasicSensor(
            "battery_health",
            "sensor",
            commonR.string.basic_sensor_name_battery_health,
            commonR.string.sensor_description_battery_health,
            "mdi:battery-heart-variant",
            deviceClass = "enum",
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC,
        )

        private val batteryTemperature = SensorManager.BasicSensor(
            "battery_temperature",
            "sensor",
            commonR.string.basic_sensor_name_battery_temperature,
            commonR.string.sensor_description_battery_temperature,
            "mdi:battery",
            deviceClass = "temperature",
            unitOfMeasurement = "°C",
            stateClass = SensorManager.STATE_CLASS_MEASUREMENT,
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC,
        )

        private val batteryPower = SensorManager.BasicSensor(
            "battery_power",
            "sensor",
            commonR.string.basic_sensor_name_battery_power,
            commonR.string.sensor_description_battery_power,
            "mdi:battery-plus",
            "power",
            unitOfMeasurement = "W",
            stateClass = SensorManager.STATE_CLASS_MEASUREMENT,
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC,
        )

        private val remainingChargeTime = SensorManager.BasicSensor(
            "remaining_charge_time",
            "sensor",
            commonR.string.basic_sensor_name_remaining_charge_time,
            commonR.string.sensor_description_remaining_charge_time,
            "mdi:battery-clock",
            "duration",
            unitOfMeasurement = "min",
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC,
        )

        private val batteryCycles = SensorManager.BasicSensor(
            "battery_cycles",
            "sensor",
            commonR.string.basic_sensor_name_battery_cycles,
            commonR.string.sensor_description_battery_cycles,
            "mdi:battery-sync",
            stateClass = SensorManager.STATE_CLASS_TOTAL_INCREASING,
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC,
        )

        fun getIsCharging(intent: Intent): Boolean {
            val status: Int = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)

            return status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
        }
    }

    override fun docsLink(): String {
        return "https://companion.home-assistant.io/docs/core/sensors#battery-sensors"
    }

    override val name: Int
        get() = commonR.string.sensor_name_battery

    private val defaultSensorList = listOf(
        batteryLevel,
        batteryState,
        isChargingState,
        chargerTypeState,
        batteryHealthState,
        batteryTemperature,
        batteryPower,
    )

    override suspend fun getAvailableSensors(context: Context): List<SensorManager.BasicSensor> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            defaultSensorList.plus(listOf(remainingChargeTime, batteryCycles))
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            defaultSensorList.plus(remainingChargeTime)
        } else {
            defaultSensorList
        }
    }

    override fun hasSensor(context: Context): Boolean {
        val intent = ContextCompat.registerReceiver(
            context,
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        return intent?.getBooleanExtra(BatteryManager.EXTRA_PRESENT, false) == true
    }

    override fun requiredPermissions(context: Context, sensorId: String): Array<String> {
        return emptyArray()
    }

    override suspend fun requestSensorUpdate(context: Context) {
        val intent = ContextCompat.registerReceiver(
            context,
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        if (intent != null) {
            updateBatteryLevel(context, intent)
            updateBatteryState(context, intent)
            updateIsCharging(context, intent)
            updateChargerType(context, intent)
            updateBatteryHealth(context, intent)
            updateBatteryTemperature(context, intent)
            updateBatteryPower(context, intent)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                updateRemainingChargeTime(context)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                updateBatteryCycles(context, intent)
            }
        }
    }

    private fun getBatteryPercentage(intent: Intent): Int {
        val level: Int = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale: Int = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        return (level.toFloat() / scale.toFloat() * 100.0f).toInt()
    }

    private suspend fun updateBatteryLevel(context: Context, intent: Intent) {
        if (!isEnabled(context, batteryLevel)) {
            return
        }

        val percentage = getBatteryPercentage(intent)
        val baseIcon = when (getChargingStatus(intent)) {
            "charging", "full" -> when (getChargerType(intent)) {
                "wireless" -> "mdi:battery-charging-wireless"
                else -> "mdi:battery-charging"
            }
            else -> "mdi:battery"
        }
        val roundedPercentage = (percentage / 10) * 10
        val icon = when (percentage) {
            in 0..100 -> baseIcon + when (percentage) {
                in 0..9 -> "-outline"
                100 -> ""
                else -> "-$roundedPercentage"
            }
            else -> "mdi:battery-unknown"
        }

        onSensorUpdated(
            context,
            batteryLevel,
            percentage,
            icon,
            mapOf(),
        )
    }

    private suspend fun updateBatteryState(context: Context, intent: Intent) {
        if (!isEnabled(context, batteryState)) {
            return
        }

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
            mapOf(
                "options" to listOf("charging", "discharging", "full", "not_charging"),
            ),
        )
    }

    private suspend fun updateIsCharging(context: Context, intent: Intent) {
        if (!isEnabled(context, isChargingState)) {
            return
        }

        val isCharging = getIsCharging(intent)

        val icon = if (isCharging) "mdi:power-plug" else "mdi:power-plug-off"
        onSensorUpdated(
            context,
            isChargingState,
            isCharging,
            icon,
            mapOf(),
        )
    }

    private suspend fun updateChargerType(context: Context, intent: Intent) {
        if (!isEnabled(context, chargerTypeState)) {
            return
        }

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
            mapOf(
                "options" to listOf("ac", "usb", "wireless", "dock", "none"),
            ),
        )
    }

    private suspend fun updateBatteryHealth(context: Context, intent: Intent) {
        if (!isEnabled(context, batteryHealthState)) {
            return
        }

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
            mapOf(
                "options" to listOf("cold", "dead", "good", "overheated", "over_voltage", "failed"),
            ),
        )
    }

    private suspend fun updateBatteryTemperature(context: Context, intent: Intent) {
        if (!isEnabled(context, batteryTemperature)) {
            return
        }

        val batteryTemp = getBatteryTemperature(intent)

        onSensorUpdated(
            context,
            batteryTemperature,
            batteryTemp,
            batteryTemperature.statelessIcon,
            mapOf(),
        )
    }

    private suspend fun updateBatteryPower(context: Context, intent: Intent) {
        if (!isEnabled(context, batteryPower)) {
            return
        }

        val voltage = getBatteryVolts(context, intent)
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val current = getBatteryCurrent(context, batteryManager)
        var wattage: Float? = null
        var icon = batteryPower.statelessIcon
        if (voltage == null || current == null) {
            Timber.w("Invalid voltage/current for battery power: $voltage/$current")
        } else {
            wattage = voltage * current
            if (wattage <= 0) icon = "mdi:battery-minus"
        }

        onSensorUpdated(
            context,
            batteryPower,
            wattage?.toBigDecimal()?.setScale(2, RoundingMode.HALF_UP) ?: STATE_UNAVAILABLE,
            icon,
            mapOf(
                "current" to current,
                "voltage" to voltage,
            ),
        )
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private suspend fun updateRemainingChargeTime(context: Context) {
        if (!isEnabled(context, remainingChargeTime)) {
            return
        }

        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val chargeTime = batteryManager.computeChargeTimeRemaining()
        val remainingCharge = if (chargeTime >= 0) {
            chargeTime.toFloat() / 60000f
        } else {
            STATE_UNAVAILABLE
        }

        onSensorUpdated(
            context,
            remainingChargeTime,
            if (chargeTime >= 0) {
                floor(remainingCharge as Float).toInt()
            } else {
                remainingCharge
            },
            remainingChargeTime.statelessIcon,
            mapOf(),
        )
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private suspend fun updateBatteryCycles(context: Context, intent: Intent) {
        if (!isEnabled(context, batteryCycles)) {
            return
        }

        val cycles = intent.getIntExtra(BatteryManager.EXTRA_CYCLE_COUNT, -1)

        onSensorUpdated(
            context,
            batteryCycles,
            if (cycles != -1) cycles else STATE_UNAVAILABLE,
            batteryCycles.statelessIcon,
            mapOf(),
        )
    }

    private fun getChargerType(intent: Intent): String {
        return when (intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)) {
            BatteryManager.BATTERY_PLUGGED_AC -> "ac"
            BatteryManager.BATTERY_PLUGGED_USB -> "usb"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "wireless"
            BatteryManager.BATTERY_PLUGGED_DOCK -> "dock"
            else -> "none"
        }
    }

    private fun getChargingStatus(intent: Intent): String {
        return when (intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)) {
            BatteryManager.BATTERY_STATUS_FULL -> "full"
            BatteryManager.BATTERY_STATUS_CHARGING -> "charging"
            BatteryManager.BATTERY_STATUS_DISCHARGING -> "discharging"
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "not_charging"
            else -> STATE_UNKNOWN
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
            else -> STATE_UNKNOWN
        }
    }

    private fun getBatteryTemperature(intent: Intent): Float {
        return intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10f
    }

    private suspend fun getBatteryCurrent(context: Context, batteryManager: BatteryManager): Float? {
        val current = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        return if (
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && current != Int.MIN_VALUE) ||
            current != 0
        ) {
            val dividerSetting = getNumberSetting(
                context,
                batteryPower,
                SETTING_BATTERY_CURRENT_DIVISOR,
                DEFAULT_BATTERY_CURRENT_DIVISOR,
            )
            current / dividerSetting.toFloat()
        } else {
            null
        }
    }

    private suspend fun getBatteryVolts(context: Context, intent: Intent): Float? {
        val voltage = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)
        return if (voltage != 0) {
            val dividerSetting = getNumberSetting(
                context,
                batteryPower,
                SETTING_BATTERY_VOLTAGE_DIVISOR,
                DEFAULT_BATTERY_VOLTAGE_DIVISOR,
            )
            voltage / dividerSetting.toFloat()
        } else {
            null
        }
    }
}
