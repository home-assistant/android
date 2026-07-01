package io.homeassistant.companion.android.common.sensors

import android.content.Context
import android.os.PowerManager
import androidx.core.content.getSystemService
import dagger.hilt.android.qualifiers.ApplicationContext
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.servers.ServerManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PowerSensorManager @Inject constructor(
    @ApplicationContext override val applicationContext: Context,
    override val sensorRepository: SensorRepository,
    override val serverManager: ServerManager,
) : SensorManager {
    companion object {
        private const val PACKAGE_NAME = "io.homeassistant.companion.android"

        @ProvidesSensor
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

        @ProvidesSensor
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

        @ProvidesSensor
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

    override suspend fun getAvailableSensors(): List<SensorManager.BasicSensor> {
        return listOf(interactiveDevice, doze, powerSave)
    }

    override fun requiredPermissions(sensorId: String): Array<String> {
        return emptyArray()
    }

    override suspend fun requestSensorUpdate() {
        val powerManager = applicationContext.getSystemService<PowerManager>()!!
        updateInteractive(powerManager)
        updatePowerSave(powerManager)
        updateDoze(powerManager)
    }

    private suspend fun updateInteractive(powerManager: PowerManager) {
        if (!isEnabled(interactiveDevice)) {
            return
        }

        val interactiveState = powerManager.isInteractive
        val icon = if (interactiveState) "mdi:cellphone" else "mdi:cellphone-off"

        onSensorUpdated(
            interactiveDevice,
            interactiveState,
            icon,
            mapOf(),
        )
    }

    private suspend fun updateDoze(powerManager: PowerManager) {
        if (!isEnabled(doze)) {
            return
        }

        val dozeState = powerManager.isDeviceIdleMode
        val icon = if (dozeState) "mdi:sleep" else "mdi:sleep-off"

        onSensorUpdated(
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

    private suspend fun updatePowerSave(powerManager: PowerManager) {
        if (!isEnabled(powerSave)) {
            return
        }

        val powerSaveState = powerManager.isPowerSaveMode

        onSensorUpdated(
            powerSave,
            powerSaveState,
            powerSave.statelessIcon,
            mapOf(),
        )
    }
}
