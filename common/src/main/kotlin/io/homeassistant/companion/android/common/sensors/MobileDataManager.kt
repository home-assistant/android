package io.homeassistant.companion.android.common.sensors

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.provider.Settings.Global.getInt
import android.telephony.TelephonyManager
import androidx.core.content.getSystemService
import io.homeassistant.companion.android.common.R as commonR

class MobileDataManager : SensorManager {

    companion object {
        val mobileDataState = SensorManager.BasicSensor(
            "mobile_data",
            "binary_sensor",
            commonR.string.basic_sensor_name_mobile_data,
            commonR.string.sensor_description_mobile_data,
            "mdi:signal",
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC,
        )
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

    override suspend fun getAvailableSensors(context: Context): List<SensorManager.BasicSensor> {
        return listOf(mobileDataState, mobileDataRoaming)
    }

    override fun requiredPermissions(context: Context, sensorId: String): Array<String> {
        return if (sensorId == mobileDataRoaming.id || Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            arrayOf(Manifest.permission.READ_PHONE_STATE)
        } else {
            arrayOf()
        }
    }

    override fun hasSensor(context: Context): Boolean {
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)
    }

    override suspend fun requestSensorUpdate(context: Context) {
        checkState(context, mobileDataState, "mobile_data", mobileDataState.statelessIcon)
        checkState(context, mobileDataRoaming, Settings.Global.DATA_ROAMING, mobileDataRoaming.statelessIcon)
    }

    private suspend fun checkState(
        context: Context,
        sensor: SensorManager.BasicSensor,
        settingKey: String,
        icon: String,
    ) {
        if (!isEnabled(context, sensor)) {
            return
        }

        var enabled = false
        val telephonyManager = context.applicationContext.getSystemService<TelephonyManager>()
        if (telephonyManager?.simState == TelephonyManager.SIM_STATE_READY) {
            enabled = if (sensor.id == mobileDataRoaming.id && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                telephonyManager.isDataRoamingEnabled
            } else if (sensor.id == mobileDataState.id && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                telephonyManager.isDataEnabled
            } else {
                getInt(context.contentResolver, settingKey, 0) == 1
            }
        }
        onSensorUpdated(
            context,
            sensor,
            enabled,
            if (enabled) icon else "$icon-off",
            mapOf(),
        )
    }
}
