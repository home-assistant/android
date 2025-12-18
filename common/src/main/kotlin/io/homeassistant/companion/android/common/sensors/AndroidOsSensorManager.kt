package io.homeassistant.companion.android.common.sensors

import android.content.Context
import android.os.Build
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.util.STATE_UNKNOWN

class AndroidOsSensorManager : SensorManager {

    companion object {
        val osVersion = SensorManager.BasicSensor(
            "android_os_version",
            "sensor",
            commonR.string.basic_sensor_name_android_os_version,
            commonR.string.sensor_description_android_os_version,
            "mdi:android",
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC,
        )
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

    override suspend fun getAvailableSensors(context: Context): List<SensorManager.BasicSensor> {
        return listOf(osVersion, osSecurityPatch)
    }

    override fun requiredPermissions(context: Context, sensorId: String): Array<String> {
        return arrayOf()
    }

    override suspend fun requestSensorUpdate(context: Context) {
        checkState(context, osVersion)
        checkState(context, osSecurityPatch)
    }

    private suspend fun checkState(context: Context, sensor: SensorManager.BasicSensor) {
        if (!isEnabled(context, sensor)) {
            return
        }

        onSensorUpdated(
            context,
            sensor,
            when (sensor.id) {
                osVersion.id -> Build.VERSION.RELEASE
                osSecurityPatch.id -> Build.VERSION.SECURITY_PATCH
                else -> STATE_UNKNOWN
            },
            sensor.statelessIcon,
            mapOf(),
        )
    }
}
