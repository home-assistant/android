package io.homeassistant.companion.android.common.sensors

import android.content.Context
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.util.STATE_UNKNOWN
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidOsSensorManager @Inject constructor(
    @ApplicationContext override val applicationContext: Context,
    override val sensorRepository: SensorRepository,
    override val serverManager: ServerManager,
) : SensorManager {

    companion object {
        @ProvidesSensor
        val osVersion = SensorManager.BasicSensor(
            "android_os_version",
            "sensor",
            commonR.string.basic_sensor_name_android_os_version,
            commonR.string.sensor_description_android_os_version,
            "mdi:android",
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC,
        )

        @ProvidesSensor
        val osSecurityPatch = SensorManager.BasicSensor(
            "android_os_security_patch",
            "sensor",
            commonR.string.basic_sensor_name_android_os_security_patch,
            commonR.string.sensor_description_android_os_security_patch,
            "mdi:security",
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC,
        )
    }

    override fun docsLink(): String {
        return "https://companion.home-assistant.io/docs/core/sensors#android-os-sensors"
    }

    override val name: Int
        get() = commonR.string.sensor_name_android_os

    override suspend fun getAvailableSensors(): List<SensorManager.BasicSensor> {
        return listOf(osVersion, osSecurityPatch)
    }

    override fun requiredPermissions(sensorId: String): Array<String> {
        return arrayOf()
    }

    override suspend fun requestSensorUpdate() {
        checkState(osVersion)
        checkState(osSecurityPatch)
    }

    private suspend fun checkState(sensor: SensorManager.BasicSensor) {
        if (!isEnabled(sensor)) {
            return
        }

        onSensorUpdated(
            sensor,
            when (sensor.id) {
                osVersion.id -> Build.VERSION.RELEASE
                osSecurityPatch.id -> Build.VERSION.SECURITY_PATCH
                else -> STATE_UNKNOWN
            },
            sensor.statelessIcon,
            emptyMap(),
        )
    }
}
