package io.homeassistant.companion.android.common.sensors

import android.app.KeyguardManager
import android.content.Context
import androidx.core.content.getSystemService
import dagger.hilt.android.qualifiers.ApplicationContext
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.servers.ServerManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KeyguardSensorManager @Inject constructor(
    @ApplicationContext override val applicationContext: Context,
    override val sensorRepository: SensorRepository,
    override val serverManager: ServerManager,
) : SensorManager {
    companion object {
        @ProvidesSensor
        val deviceLocked = SensorManager.BasicSensor(
            "device_locked",
            "binary_sensor",
            commonR.string.basic_sensor_name_device_locked,
            commonR.string.sensor_description_device_locked,
            "mdi:cellphone-lock",
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC,
        )

        @ProvidesSensor
        val deviceSecure = SensorManager.BasicSensor(
            "device_secure",
            "binary_sensor",
            commonR.string.basic_sensor_name_device_secure,
            commonR.string.sensor_description_device_secure,
            "mdi:cellphone-key",
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC,
        )

        @ProvidesSensor
        val keyguardLocked = SensorManager.BasicSensor(
            "keyguard_locked",
            "binary_sensor",
            commonR.string.basic_sensor_name_keyguard_locked,
            commonR.string.sensor_description_keyguard_locked,
            "mdi:cellphone-lock",
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC,
        )

        @ProvidesSensor
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

    override suspend fun getAvailableSensors(): List<SensorManager.BasicSensor> {
        return listOf(deviceLocked, deviceSecure, keyguardLocked, keyguardSecure)
    }

    override fun requiredPermissions(sensorId: String): Array<String> {
        return emptyArray()
    }

    override suspend fun requestSensorUpdate() {
        val km = applicationContext.getSystemService<KeyguardManager>()!!
        updateDeviceLocked(km)
        updateDeviceSecure(km)

        updateKeyguardLocked(km)
        updateKeyguardSecure(km)
    }

    private suspend fun updateDeviceLocked(km: KeyguardManager) {
        if (!isEnabled(deviceLocked)) {
            return
        }

        val isLocked = km.isDeviceLocked
        val icon = if (isLocked) "mdi:cellphone-lock" else "mdi:cellphone"

        onSensorUpdated(
            deviceLocked,
            isLocked,
            icon,
            mapOf(),
        )
    }

    private suspend fun updateDeviceSecure(km: KeyguardManager) {
        if (!isEnabled(deviceSecure)) {
            return
        }

        val isSecure = km.isDeviceSecure
        val icon = if (isSecure) "mdi:cellphone-key" else "mdi:cellphone"

        onSensorUpdated(
            deviceSecure,
            isSecure,
            icon,
            mapOf(),
        )
    }

    private suspend fun updateKeyguardLocked(km: KeyguardManager) {
        if (!isEnabled(keyguardLocked)) {
            return
        }

        val isLocked = km.isKeyguardLocked
        val icon = if (isLocked) "mdi:cellphone-lock" else "mdi:cellphone"

        onSensorUpdated(
            keyguardLocked,
            isLocked,
            icon,
            mapOf(),
        )
    }

    private suspend fun updateKeyguardSecure(km: KeyguardManager) {
        if (!isEnabled(keyguardSecure)) {
            return
        }

        val isSecure = km.isKeyguardSecure
        val icon = if (isSecure) "mdi:cellphone-key" else "mdi:cellphone"

        onSensorUpdated(
            keyguardSecure,
            isSecure,
            icon,
            mapOf(),
        )
    }
}
