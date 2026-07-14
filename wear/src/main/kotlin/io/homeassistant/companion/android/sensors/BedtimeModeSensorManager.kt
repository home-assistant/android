package io.homeassistant.companion.android.sensors

import android.content.Context
import android.os.Build
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.sensors.ProvidesSensor
import io.homeassistant.companion.android.common.sensors.SensorManager
import io.homeassistant.companion.android.common.sensors.SensorRepository
import io.homeassistant.companion.android.common.util.SdkVersion
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

@Singleton
class BedtimeModeSensorManager @Inject constructor(
    @ApplicationContext override val applicationContext: Context,
    override val sensorRepository: SensorRepository,
    override val serverManager: ServerManager,
) : SensorManager {
    companion object {
        @ProvidesSensor
        val bedtimeMode = SensorManager.BasicSensor(
            "bedtime_mode",
            "binary_sensor",
            commonR.string.sensor_name_bedtime_mode,
            commonR.string.sensor_description_bedtime_mode,
            "mdi:sleep",
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC,
            updateType = SensorManager.BasicSensor.UpdateType.WORKER,
        )
    }

    override fun docsLink(): String {
        return "https://companion.home-assistant.io/docs/wear-os/sensors"
    }
    override val name: Int
        get() = commonR.string.sensor_name_bedtime_mode

    override suspend fun getAvailableSensors(): List<SensorManager.BasicSensor> {
        return listOf(bedtimeMode)
    }

    override fun requiredPermissions(sensorId: String): Array<String> {
        return emptyArray()
    }

    override suspend fun requestSensorUpdate() {
        updateBedtimeMode()
    }

    override fun hasSensor(): Boolean {
        return SdkVersion.isAtLeast(Build.VERSION_CODES.R)
    }

    private suspend fun updateBedtimeMode() {
        if (!isEnabled(bedtimeMode)) {
            return
        }

        val state = try {
            Settings.Global.getInt(
                applicationContext.contentResolver,
                if (Build.MANUFACTURER ==
                    "samsung"
                ) {
                    "setting_bedtime_mode_running_state"
                } else {
                    "bedtime_mode"
                },
            ) ==
                1
        } catch (e: Exception) {
            Timber.e(e, "Unable to update bedtime mode sensor")
            false
        }

        onSensorUpdated(
            bedtimeMode,
            state,
            if (!state) "mdi:sleep-off" else bedtimeMode.statelessIcon,
            mapOf(),
        )
    }
}
