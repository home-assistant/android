package io.homeassistant.companion.android.common.sensors

import android.app.KeyguardManager
import android.content.Context
import androidx.core.content.getSystemService
import io.homeassistant.companion.android.common.R as commonR

class KeyguardSensorManager : SensorManager {
    companion object {
        val deviceLocked = SensorManager.BasicSensor(
            "device_locked",
            "binary_sensor",
            commonR.string.basic_sensor_name_device_locked,
            commonR.string.sensor_description_device_locked,
            "mdi:cellphone-lock",
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC,
        )
        val deviceSecure = SensorManager.BasicSensor(
            "device_secure",
            "binary_sensor",
            commonR.string.basic_sensor_name_device_secure,
            commonR.string.sensor_description_device_secure,
            "mdi:cellphone-key",
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC,
        )
        val keyguardLocked = SensorManager.BasicSensor(
            "keyguard_locked",
            "binary_sensor",
            commonR.string.basic_sensor_name_keyguard_locked,
            commonR.string.sensor_description_keyguard_locked,
            "mdi:cellphone-lock",
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC,
        )
        val keyguardSecure = SensorManager.BasicSensor(
            "keyguard_secure",
            "binary_sensor",
            commonR.string.basic_sensor_name_keyguard_secure,
            commonR.string.sensor_description_keyguard_secure,
            "mdi:cellphone-key",
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC,
        )
    }

    override fun docsLink(): String {
        return "https://companion.home-assistant.io/docs/core/sensors#keyguard-sensors"
    }

    override val name: Int
        get() = commonR.string.sensor_name_keyguard

    override suspend fun getAvailableSensors(context: Context): List<SensorManager.BasicSensor> {
        return listOf(deviceLocked, deviceSecure, keyguardLocked, keyguardSecure)
    }

    override fun requiredPermissions(context: Context, sensorId: String): Array<String> {
        return emptyArray()
    }

    override suspend fun requestSensorUpdate(context: Context) {
        val km = context.getSystemService<KeyguardManager>()!!
        updateDeviceLocked(context, km)
        updateDeviceSecure(context, km)

        updateKeyguardLocked(context, km)
        updateKeyguardSecure(context, km)
    }

    private suspend fun updateDeviceLocked(context: Context, km: KeyguardManager) {
        if (!isEnabled(context, deviceLocked)) {
            return
        }

        val isLocked = km.isDeviceLocked
        val icon = if (isLocked) "mdi:cellphone-lock" else "mdi:cellphone"

        onSensorUpdated(
            context,
            deviceLocked,
            isLocked,
            icon,
            mapOf(),
        )
    }

    private suspend fun updateDeviceSecure(context: Context, km: KeyguardManager) {
        if (!isEnabled(context, deviceSecure)) {
            return
        }

        val isSecure = km.isDeviceSecure
        val icon = if (isSecure) "mdi:cellphone-key" else "mdi:cellphone"

        onSensorUpdated(
            context,
            deviceSecure,
            isSecure,
            icon,
            mapOf(),
        )
    }

    private suspend fun updateKeyguardLocked(context: Context, km: KeyguardManager) {
        if (!isEnabled(context, keyguardLocked)) {
            return
        }

        val isLocked = km.isKeyguardLocked
        val icon = if (isLocked) "mdi:cellphone-lock" else "mdi:cellphone"

        onSensorUpdated(
            context,
            keyguardLocked,
            isLocked,
            icon,
            mapOf(),
        )
    }

    private suspend fun updateKeyguardSecure(context: Context, km: KeyguardManager) {
        if (!isEnabled(context, keyguardSecure)) {
            return
        }

        val isSecure = km.isKeyguardSecure
        val icon = if (isSecure) "mdi:cellphone-key" else "mdi:cellphone"

        onSensorUpdated(
            context,
            keyguardSecure,
            isSecure,
            icon,
            mapOf(),
        )
    }
}
