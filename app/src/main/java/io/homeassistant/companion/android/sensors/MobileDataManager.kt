package io.homeassistant.companion.android.sensors

import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import android.provider.Settings.Global.getInt
import android.telephony.TelephonyManager
import io.homeassistant.companion.android.R

class MobileDataManager : SensorManager {

    companion object {
        val mobileDataState = SensorManager.BasicSensor(
            "mobile_data",
            "binary_sensor",
            R.string.basic_sensor_name_mobile_data,
            R.string.sensor_description_mobile_data
        )
        val mobileDataRoaming = SensorManager.BasicSensor(
            "mobile_data_roaming",
            "binary_sensor",
            R.string.basic_sensor_name_mobile_data_roaming,
            R.string.sensor_description_mobile_data_roaming
        )
    }

    override val enabledByDefault: Boolean
        get() = false
    override val name: Int
        get() = R.string.sensor_name_mobile_data

    override fun getAvailableSensors(context: Context): List<SensorManager.BasicSensor> {
        return listOf(mobileDataState, mobileDataRoaming)
    }

    override fun requiredPermissions(sensorId: String): Array<String> {
        return arrayOf()
    }

    override fun hasSensor(context: Context): Boolean {
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)
    }

    override fun requestSensorUpdate(
        context: Context
    ) {
        checkState(context, mobileDataState, "mobile_data", "mdi:signal")
        checkState(context, mobileDataRoaming, Settings.Global.DATA_ROAMING, "mdi:toggle-switch")
    }

    private fun checkState(
        context: Context,
        sensor: SensorManager.BasicSensor,
        settingKey: String,
        icon: String
    ) {
        if (!isEnabled(context, sensor.id))
            return

        var enabled = false
        val telephonyManager =
            (context.applicationContext.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager)
        if (telephonyManager.simState == TelephonyManager.SIM_STATE_READY) {
            enabled = getInt(context.contentResolver, settingKey, 0) == 1
        }
        onSensorUpdated(
            context,
            sensor,
            enabled,
            if (enabled) icon else "$icon-off",
            mapOf()
        )
    }
}
