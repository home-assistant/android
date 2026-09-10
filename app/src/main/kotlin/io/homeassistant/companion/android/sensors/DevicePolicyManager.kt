package io.homeassistant.companion.android.sensors

import android.content.Context
import android.content.Intent
import dagger.hilt.android.qualifiers.ApplicationContext
import io.homeassistant.companion.android.common.R
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.sensors.ProvidesSensor
import io.homeassistant.companion.android.common.sensors.SensorManager
import io.homeassistant.companion.android.common.sensors.SensorRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DevicePolicyManager @Inject constructor(
    @ApplicationContext override val applicationContext: Context,
    override val sensorRepository: SensorRepository,
    override val serverManager: ServerManager,
) : SensorManager {
    companion object {
        @ProvidesSensor
        val isWorkProfile = SensorManager.BasicSensor(
            "is_work_profile",
            "binary_sensor",
            R.string.sensor_name_work_profile,
            R.string.sensor_description_work_profile,
            "mdi:briefcase",
            updateType = SensorManager.BasicSensor.UpdateType.INTENT_ONLY,
        )
    }

    override fun docsLink(): String {
        return "https://companion.home-assistant.io/docs/core/sensors#work-profile-sensor"
    }

    private var isManagedProfileAvailable: Boolean? = null

    override val name: Int
        get() = R.string.sensor_name_device_policy

    override suspend fun getAvailableSensors(): List<SensorManager.BasicSensor> {
        return listOf(isWorkProfile)
    }

    override fun requiredPermissions(sensorId: String): Array<String> {
        return emptyArray()
    }

    override suspend fun requestSensorUpdate() {
        updateWorkProfile(applicationContext)
    }

    override suspend fun requestSensorUpdate(intent: Intent?) {
        // The intent has the only record we get of this state, so save it off in our instance
        if (intent?.action == Intent.ACTION_MANAGED_PROFILE_AVAILABLE) {
            isManagedProfileAvailable = true
        } else if (intent?.action == Intent.ACTION_MANAGED_PROFILE_UNAVAILABLE) {
            isManagedProfileAvailable = false
        }

        updateWorkProfile(applicationContext)
    }

    private suspend fun updateWorkProfile(applicationContext: Context) {
        if (!isEnabled(isWorkProfile)) {
            return
        }

        isManagedProfileAvailable?.let { state ->
            onSensorUpdated(
                isWorkProfile,
                state,
                isWorkProfile.statelessIcon,
                mapOf(),
            )
        }
    }
}
