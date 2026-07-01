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
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

@Singleton
class TheaterModeSensorManager @Inject constructor(
    @ApplicationContext override val applicationContext: Context,
    override val sensorRepository: SensorRepository,
    override val serverManager: ServerManager,
) : SensorManager {
    companion object {
        @ProvidesSensor
        val theaterMode = SensorManager.BasicSensor(
            "theater_mode",
            "binary_sensor",
            commonR.string.sensor_name_theater_mode,
            commonR.string.sensor_description_theater_mode,
            "mdi:movie-open",
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC,
            updateType = SensorManager.BasicSensor.UpdateType.WORKER,
        )
    }

    override fun docsLink(): String {
        return "https://companion.home-assistant.io/docs/wear-os/sensors"
    }
    override val name: Int
        get() = commonR.string.sensor_name_theater_mode

    override suspend fun getAvailableSensors(): List<SensorManager.BasicSensor> {
        return listOf(theaterMode)
    }

    override fun requiredPermissions(sensorId: String): Array<String> {
        return emptyArray()
    }

    override suspend fun requestSensorUpdate() {
        updateTheaterMode()
    }

    private suspend fun updateTheaterMode() {
        if (!isEnabled(theaterMode)) {
            return
        }

        val state = try {
            Settings.Global.getInt(
                applicationContext.contentResolver,
                if (Build.MANUFACTURER ==
                    "samsung"
                ) {
                    "setting_theater_mode_on"
                } else {
                    "theater_mode_on"
                },
            ) ==
                1
        } catch (e: Exception) {
            Timber.e(e, "Unable to update theater mode sensor")
            false
        }

        onSensorUpdated(
            theaterMode,
            state,
            if (!state) "mdi:movie-open-off" else theaterMode.statelessIcon,
            mapOf(),
        )
    }
}
