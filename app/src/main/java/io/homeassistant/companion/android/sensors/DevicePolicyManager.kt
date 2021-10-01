package io.homeassistant.companion.android.sensors

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.util.Log
import io.homeassistant.companion.android.R

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

    private fun updateWorkProfile(context: Context) {

        if (!isEnabled(context, isWorkProfile.id))
            return

        var isWorkProfileEnabled = false
        try {
            val devicePolicyManager = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

            val activeAdmins = devicePolicyManager.activeAdmins

            if (activeAdmins != null) {
                for (admin in activeAdmins) {
                    if (devicePolicyManager.isProfileOwnerApp(admin.packageName))
                        isWorkProfileEnabled = true
                }
            }

            val icon = "mdi:briefcase"

            onSensorUpdated(
                context,
                isWorkProfile,
                isWorkProfileEnabled,
                icon,
                mapOf()
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error getting the devices work profile", e)
        }
    }
}
