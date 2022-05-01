package io.homeassistant.companion.android.common.sensors

import android.content.Context
import android.util.Log
import io.homeassistant.companion.android.database.AppDatabase
import io.homeassistant.companion.android.database.sensor.SensorSetting
import io.homeassistant.companion.android.database.sensor.SensorSettingType
import io.homeassistant.companion.android.common.R as commonR

class LastUpdateManager : SensorManager {
    companion object {
        private const val TAG = "LastUpdate"
        private const val SETTING_ADD_NEW_INTENT = "lastupdate_add_new_intent"

        val lastUpdate = SensorManager.BasicSensor(
            "last_update",
            "sensor",
            commonR.string.basic_sensor_name_last_update,
            commonR.string.sensor_description_last_update,
            "mdi:update",
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC,
            updateType = SensorManager.BasicSensor.UpdateType.INTENT
        )
    }

    override fun docsLink(): String {
        return "https://companion.home-assistant.io/docs/core/sensors#last-update-trigger-sensor"
    }
    override val enabledByDefault: Boolean
        get() = false
    override val name: Int
        get() = commonR.string.sensor_name_last_update

    override fun getAvailableSensors(context: Context): List<SensorManager.BasicSensor> {
        return listOf(lastUpdate)
    }

    override fun requiredPermissions(sensorId: String): Array<String> {
        return emptyArray()
    }

    override fun requestSensorUpdate(
        context: Context
    ) {
        // No op
    }

    fun sendLastUpdate(context: Context, intentAction: String?) {

        if (!isEnabled(context, lastUpdate.id))
            return

        if (intentAction.isNullOrEmpty())
            return

        Log.d(TAG, "Last update is $intentAction")

        onSensorUpdated(
            context,
            lastUpdate,
            intentAction,
            lastUpdate.statelessIcon,
            mapOf()
        )

        val sensorDao = AppDatabase.getInstance(context).sensorDao()
        val allSettings = sensorDao.getSettings(lastUpdate.id)
        val intentSettingName = "lastupdate_intent_var1:${allSettings.size}:"
        val addNewIntent = allSettings.firstOrNull { it.name == SETTING_ADD_NEW_INTENT }?.value ?: "false"
        val intentSetting = allSettings.firstOrNull { it.name == intentSettingName }?.value ?: ""
        if (addNewIntent == "true") {
            if (intentSetting == "") {
                sensorDao.add(SensorSetting(lastUpdate.id, SETTING_ADD_NEW_INTENT, "false", SensorSettingType.TOGGLE))
                sensorDao.add(SensorSetting(lastUpdate.id, intentSettingName, intentAction, SensorSettingType.STRING))
            }
        } else {
            sensorDao.add(SensorSetting(lastUpdate.id, SETTING_ADD_NEW_INTENT, "false", SensorSettingType.TOGGLE))
        }
        for (setting in allSettings) {
            if (setting.value == "")
                sensorDao.removeSetting(lastUpdate.id, setting.name)
        }
    }
}
