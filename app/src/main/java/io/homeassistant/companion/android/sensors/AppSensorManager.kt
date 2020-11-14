package io.homeassistant.companion.android.sensors

import android.content.Context
import io.homeassistant.companion.android.BuildConfig
import io.homeassistant.companion.android.R

class AppSensorManager : SensorManager {
    companion object {
        private const val TAG = "AppSensor"

        val currentVersion = SensorManager.BasicSensor(
            "current_version",
            "sensor",
            R.string.basic_sensor_name_current_version,
            R.string.sensor_description_current_version
        )
    }

    override val enabledByDefault: Boolean
        get() = false
    override val name: Int
        get() = R.string.sensor_name_app_sensor

    override val availableSensors: List<SensorManager.BasicSensor>
        get() = listOf(currentVersion)

    override fun requiredPermissions(sensorId: String): Array<String> {
        return emptyArray()
    }

    override fun requestSensorUpdate(
        context: Context
    ) {
        updateCurrentVersion(context)
    }

    private fun updateCurrentVersion(context: Context) {

        if (!isEnabled(context, currentVersion.id))
            return

        val state = BuildConfig.VERSION_NAME
        val icon = "mdi:android"

        onSensorUpdated(context,
            currentVersion,
            state,
            icon,
            mapOf()
        )
    }
}
