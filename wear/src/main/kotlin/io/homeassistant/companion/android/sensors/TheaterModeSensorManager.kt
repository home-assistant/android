package io.homeassistant.companion.android.sensors

import android.content.Context
import android.os.Build
import android.provider.Settings
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.sensors.SensorManager
import timber.log.Timber

class TheaterModeSensorManager : SensorManager {
    companion object {
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

    override suspend fun getAvailableSensors(context: Context): List<SensorManager.BasicSensor> {
        return listOf(theaterMode)
    }

    override fun requiredPermissions(context: Context, sensorId: String): Array<String> {
        return emptyArray()
    }

    override suspend fun requestSensorUpdate(context: Context) {
        updateTheaterMode(context)
    }

    private suspend fun updateTheaterMode(context: Context) {
        if (!isEnabled(context, theaterMode)) {
            return
        }

        val state = try {
            Settings.Global.getInt(
                context.contentResolver,
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
            context,
            theaterMode,
            state,
            if (!state) "mdi:movie-open-off" else theaterMode.statelessIcon,
            mapOf(),
        )
    }
}
