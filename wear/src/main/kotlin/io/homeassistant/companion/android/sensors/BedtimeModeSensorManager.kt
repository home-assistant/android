package io.homeassistant.companion.android.sensors

import android.content.Context
import android.os.Build
import android.provider.Settings
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.sensors.SensorManager
import timber.log.Timber

class BedtimeModeSensorManager : SensorManager {
    companion object {
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

    override suspend fun getAvailableSensors(context: Context): List<SensorManager.BasicSensor> {
        return listOf(bedtimeMode)
    }

    override fun requiredPermissions(context: Context, sensorId: String): Array<String> {
        return emptyArray()
    }

    override suspend fun requestSensorUpdate(context: Context) {
        updateBedtimeMode(context)
    }

    override fun hasSensor(context: Context): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
    }

    private suspend fun updateBedtimeMode(context: Context) {
        if (!isEnabled(context, bedtimeMode)) {
            return
        }

        val state = try {
            Settings.Global.getInt(
                context.contentResolver,
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
            context,
            bedtimeMode,
            state,
            if (!state) "mdi:sleep-off" else bedtimeMode.statelessIcon,
            mapOf(),
        )
    }
}
