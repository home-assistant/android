package io.homeassistant.companion.android.sensors

import android.content.Context
import android.content.Intent
import io.homeassistant.companion.android.common.R
import io.homeassistant.companion.android.common.sensors.SensorManager

class DevicePolicyManager : SensorManager {
    companion object {
        private const val TAG = "DevicePolicyMgr"

        val isWorkProfile = SensorManager.BasicSensor(
            "is_work_profile",
            "binary_sensor",
            R.string.sensor_name_work_profile,
            R.string.sensor_description_work_profile
        )
    }

    override fun docsLink(): String {
        return "https://companion.home-assistant.io/docs/core/sensors#work-profile-sensor"
    }

    private var isManagedProfileAvailable: Boolean = false

    override val enabledByDefault: Boolean
        get() = false
    override val name: Int
        get() = R.string.sensor_name_device_policy

    override fun getAvailableSensors(context: Context): List<SensorManager.BasicSensor> {
        return listOf(isWorkProfile)
    }

    override fun requiredPermissions(sensorId: String): Array<String> {
        return emptyArray()
    }

    override fun requestSensorUpdate(context: Context) {
        updateWorkProfile(context)
    }

    override fun requestSensorUpdate(context: Context, intent: Intent?) {
        // The intent has the only record we get of this state, so save it off in our instance
        if (intent?.action == Intent.ACTION_MANAGED_PROFILE_AVAILABLE) {
            isManagedProfileAvailable = true
        } else if (intent?.action == Intent.ACTION_MANAGED_PROFILE_UNAVAILABLE) {
            isManagedProfileAvailable = false
        }

        updateWorkProfile(context)
    }

    private fun updateWorkProfile(context: Context) {

        if (!isEnabled(context, isWorkProfile.id))
            return

        val icon = "mdi:briefcase"

        onSensorUpdated(
            context,
            isWorkProfile,
            isManagedProfileAvailable,
            icon,
            mapOf()
        )
    }
}
