package io.homeassistant.companion.android.sensors

import android.content.Context
import android.content.Intent
import dagger.hilt.android.qualifiers.ApplicationContext
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.sensors.ProvidesSensor
import io.homeassistant.companion.android.common.sensors.SensorManager
import io.homeassistant.companion.android.common.sensors.SensorRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WetModeSensorManager @Inject constructor(
    @ApplicationContext override val applicationContext: Context,
    override val sensorRepository: SensorRepository,
    override val serverManager: ServerManager,
) : SensorManager {
    companion object {
        @ProvidesSensor
        val wetModeSensor = SensorManager.BasicSensor(
            "wet_mode",
            "binary_sensor",
            commonR.string.sensor_name_wet_mode,
            commonR.string.sensor_description_wet_mode,
            "mdi:water-off",
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC,
            updateType = SensorManager.BasicSensor.UpdateType.INTENT_ONLY,
        )
    }

    override fun docsLink(): String {
        return "https://companion.home-assistant.io/docs/wear-os/sensors"
    }

    private var wetModeEnabled: Boolean = false

    override val name: Int
        get() = commonR.string.sensor_name_wet_mode

    override suspend fun getAvailableSensors(): List<SensorManager.BasicSensor> {
        return listOf(
            wetModeSensor,
        )
    }

    override fun requiredPermissions(sensorId: String): Array<String> {
        return emptyArray()
    }

    override suspend fun requestSensorUpdate(intent: Intent?) {
        if (intent?.action == "com.google.android.clockwork.actions.WET_MODE_STARTED") {
            wetModeEnabled = true
        } else if (intent?.action == "com.google.android.clockwork.actions.WET_MODE_ENDED") {
            wetModeEnabled = false
        }

        updateWetMode()
    }

    override suspend fun requestSensorUpdate() {
        // No Op
    }

    private suspend fun updateWetMode() {
        if (!isEnabled(wetModeSensor)) {
            return
        }

        onSensorUpdated(
            wetModeSensor,
            wetModeEnabled,
            if (wetModeEnabled) "mdi:water" else wetModeSensor.statelessIcon,
            mapOf(),
        )
    }
}
