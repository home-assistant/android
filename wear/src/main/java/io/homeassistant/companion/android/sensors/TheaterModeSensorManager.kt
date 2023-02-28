package io.homeassistant.companion.android.sensors

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.util.Log
import io.homeassistant.companion.android.common.sensors.SensorManager
import io.homeassistant.companion.android.common.R as commonR

class TheaterModeSensorManager : SensorManager {
    companion object {
        private const val TAG = "TheaterSensor"

        val theaterMode = SensorManager.BasicSensor(
            "theater_mode",
            "binary_sensor",
            commonR.string.sensor_name_theater_mode,
            commonR.string.sensor_description_theater_mode,
            "mdi:movie-open",
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC,
            updateType = SensorManager.BasicSensor.UpdateType.WORKER
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

    override fun requiredPermissions(sensorId: String): Array<String> {
        return emptyArray()
    }

    override fun requestSensorUpdate(context: Context) {
        updateTheaterMode(context)
    }

    private fun updateTheaterMode(context: Context) {
        if (!isEnabled(context, theaterMode)) {
            return
        }

        val state = try {
            Settings.Global.getInt(context.contentResolver, if (Build.MANUFACTURER == "samsung") "setting_theater_mode_on" else "theater_mode_on") == 1
        } catch (e: Exception) {
            Log.e(TAG, "Unable to update theater mode sensor", e)
            false
        }

        onSensorUpdated(
            context,
            theaterMode,
            state,
            if (!state) "mdi:movie-open-off" else theaterMode.statelessIcon,
            mapOf()
        )
    }
}
