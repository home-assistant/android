package io.homeassistant.companion.android.common.sensors

import android.content.Context
import android.os.PowerManager
import androidx.core.content.getSystemService
import io.homeassistant.companion.android.common.R as commonR

class PowerSensorManager : SensorManager {
    companion object {
        private const val PACKAGE_NAME = "io.homeassistant.companion.android"

        val interactiveDevice = SensorManager.BasicSensor(
            "is_interactive",
            "binary_sensor",
            commonR.string.basic_sensor_name_interactive,
            commonR.string.sensor_description_interactive,
            "mdi:cellphone",
            docsLink = "https://companion.home-assistant.io/docs/core/sensors#interactive-sensor",
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC,
            updateType = SensorManager.BasicSensor.UpdateType.INTENT,
        )
        val doze = SensorManager.BasicSensor(
            "is_idle",
            "binary_sensor",
            commonR.string.basic_sensor_name_doze,
            commonR.string.sensor_description_doze,
            "mdi:sleep",
            docsLink = "https://companion.home-assistant.io/docs/core/sensors#doze-sensor",
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC,
            updateType = SensorManager.BasicSensor.UpdateType.INTENT,
        )
        val powerSave = SensorManager.BasicSensor(
            "power_save",
            "binary_sensor",
            commonR.string.basic_sensor_name_power_save,
            commonR.string.sensor_description_power_save,
            "mdi:battery-plus",
            docsLink = "https://companion.home-assistant.io/docs/core/sensors#power-save-sensor",
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC,
            updateType = SensorManager.BasicSensor.UpdateType.INTENT,
        )
    }

    override val name: Int
        get() = commonR.string.sensor_name_power

    override suspend fun getAvailableSensors(context: Context): List<SensorManager.BasicSensor> {
        return listOf(interactiveDevice, doze, powerSave)
    }

    override fun requiredPermissions(context: Context, sensorId: String): Array<String> {
        return emptyArray()
    }

    override suspend fun requestSensorUpdate(context: Context) {
        val powerManager = context.getSystemService<PowerManager>()!!
        updateInteractive(context, powerManager)
        updatePowerSave(context, powerManager)
        updateDoze(context, powerManager)
    }

    private suspend fun updateInteractive(context: Context, powerManager: PowerManager) {
        if (!isEnabled(context, interactiveDevice)) {
            return
        }

        val interactiveState = powerManager.isInteractive
        val icon = if (interactiveState) "mdi:cellphone" else "mdi:cellphone-off"

        onSensorUpdated(
            context,
            interactiveDevice,
            interactiveState,
            icon,
            mapOf(),
        )
    }

    private suspend fun updateDoze(context: Context, powerManager: PowerManager) {
        if (!isEnabled(context, doze)) {
            return
        }

        val dozeState = powerManager.isDeviceIdleMode
        val icon = if (dozeState) "mdi:sleep" else "mdi:sleep-off"

        onSensorUpdated(
            context,
            doze,
            dozeState,
            icon,
            mapOf(
                "ignoring_battery_optimizations" to powerManager.isIgnoringBatteryOptimizations(
                    PACKAGE_NAME,
                ),
            ),
        )
    }

    private suspend fun updatePowerSave(context: Context, powerManager: PowerManager) {
        if (!isEnabled(context, powerSave)) {
            return
        }

        val powerSaveState = powerManager.isPowerSaveMode

        onSensorUpdated(
            context,
            powerSave,
            powerSaveState,
            powerSave.statelessIcon,
            mapOf(),
        )
    }
}
