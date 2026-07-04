package io.homeassistant.companion.android.common.sensors

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.provider.Settings.Global.getInt
import android.telephony.TelephonyManager
import androidx.core.content.getSystemService
import dagger.hilt.android.qualifiers.ApplicationContext
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.util.SdkVersion
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MobileDataManager @Inject constructor(
    @ApplicationContext override val applicationContext: Context,
    override val sensorRepository: SensorRepository,
    override val serverManager: ServerManager,
) : SensorManager {

    companion object {
        @ProvidesSensor
        val mobileDataState = SensorManager.BasicSensor(
            "mobile_data",
            "binary_sensor",
            commonR.string.basic_sensor_name_mobile_data,
            commonR.string.sensor_description_mobile_data,
            "mdi:signal",
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC,
        )

        @ProvidesSensor
        val mobileDataRoaming = SensorManager.BasicSensor(
            "mobile_data_roaming",
            "binary_sensor",
            commonR.string.basic_sensor_name_mobile_data_roaming,
            commonR.string.sensor_description_mobile_data_roaming,
            "mdi:toggle-switch",
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC,
        )
    }

    override fun docsLink(): String {
        return "https://companion.home-assistant.io/docs/core/sensors#mobile-data-sensors"
    }

    override val name: Int
        get() = commonR.string.sensor_name_mobile_data

    override suspend fun getAvailableSensors(): List<SensorManager.BasicSensor> {
        return listOf(mobileDataState, mobileDataRoaming)
    }

    override fun requiredPermissions(sensorId: String): Array<String> {
        return if (sensorId == mobileDataRoaming.id || SdkVersion.isAtLeast(Build.VERSION_CODES.O)) {
            arrayOf(Manifest.permission.READ_PHONE_STATE)
        } else {
            arrayOf()
        }
    }

    override fun hasSensor(): Boolean {
        return applicationContext.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)
    }

    override suspend fun requestSensorUpdate() {
        checkState(mobileDataState, "mobile_data", mobileDataState.statelessIcon)
        checkState(mobileDataRoaming, Settings.Global.DATA_ROAMING, mobileDataRoaming.statelessIcon)
    }

    private suspend fun checkState(sensor: SensorManager.BasicSensor, settingKey: String, icon: String) {
        if (!isEnabled(sensor)) {
            return
        }

        var enabled = false
        val telephonyManager = applicationContext.getSystemService<TelephonyManager>()
        if (telephonyManager?.simState == TelephonyManager.SIM_STATE_READY) {
            enabled = when (sensor.id) {
                mobileDataRoaming.id if SdkVersion.isAtLeast(Build.VERSION_CODES.Q) -> {
                    telephonyManager.isDataRoamingEnabled
                }
                mobileDataState.id if SdkVersion.isAtLeast(Build.VERSION_CODES.O) -> {
                    telephonyManager.isDataEnabled
                }
                else -> {
                    getInt(applicationContext.contentResolver, settingKey, 0) == 1
                }
            }
        }
        onSensorUpdated(
            sensor,
            enabled,
            if (enabled) icon else "$icon-off",
            mapOf(),
        )
    }
}
