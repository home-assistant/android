package io.homeassistant.companion.android.sensors

import android.content.Context
import android.os.Build
import android.os.PowerManager
import androidx.annotation.RequiresApi
import androidx.core.content.getSystemService
import io.homeassistant.companion.android.common.sensors.SensorManager
import io.homeassistant.companion.android.common.R as commonR

class PowerSensorManager : SensorManager {
    companion object {
        private const val TAG = "PowerSensors"
        private const val packageName = "io.homeassistant.companion.android"

        val interactiveDevice = SensorManager.BasicSensor(
            "is_interactive",
            "binary_sensor",
            commonR.string.basic_sensor_name_interactive,
            commonR.string.sensor_description_interactive,
            docsLink = "https://companion.home-assistant.io/docs/core/sensors#interactive-sensor",
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC
        )
        val doze = SensorManager.BasicSensor(
            "is_idle",
            "binary_sensor",
            commonR.string.basic_sensor_name_doze,
            commonR.string.sensor_description_doze,
            docsLink = "https://companion.home-assistant.io/docs/core/sensors#doze-sensor",
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC
        )
        val powerSave = SensorManager.BasicSensor(
            "power_save",
            "binary_sensor",
            commonR.string.basic_sensor_name_power_save,
            commonR.string.sensor_description_power_save,
            docsLink = "https://companion.home-assistant.io/docs/core/sensors#power-save-sensor",
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC
        )
    }

    override val enabledByDefault: Boolean
        get() = false
    override val name: Int
        get() = commonR.string.sensor_name_power

    override fun getAvailableSensors(context: Context): List<SensorManager.BasicSensor> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            listOf(interactiveDevice, doze, powerSave)
        } else {
            listOf(interactiveDevice, powerSave)
        }
    }

    override fun requiredPermissions(sensorId: String): Array<String> {
        return emptyArray()
    }

    override fun requestSensorUpdate(
        context: Context
    ) {
        val powerManager = context.getSystemService<PowerManager>()!!
        updateInteractive(context, powerManager)
        updatePowerSave(context, powerManager)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            updateDoze(context, powerManager)
        }
    }

    private fun updateInteractive(context: Context, powerManager: PowerManager) {

        if (!isEnabled(context, interactiveDevice.id))
            return

        val interactiveState = powerManager.isInteractive
        val icon = if (interactiveState) "mdi:cellphone" else "mdi:cellphone-off"

        onSensorUpdated(
            context,
            interactiveDevice,
            interactiveState,
            icon,
            mapOf()
        )
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun updateDoze(context: Context, powerManager: PowerManager) {

        if (!isEnabled(context, doze.id))
            return

        val dozeState = powerManager.isDeviceIdleMode
        val icon = if (dozeState) "mdi:sleep" else "mdi:sleep-off"

        onSensorUpdated(
            context,
            doze,
            dozeState,
            icon,
            mapOf(
                "ignoring_battery_optimizations" to powerManager.isIgnoringBatteryOptimizations(packageName)
            )
        )
    }

    private fun updatePowerSave(context: Context, powerManager: PowerManager) {

        if (!isEnabled(context, powerSave.id))
            return

        val powerSaveState = powerManager.isPowerSaveMode
        val icon = "mdi:battery-plus"

        onSensorUpdated(
            context,
            powerSave,
            powerSaveState,
            icon,
            mapOf()
        )
    }
}
