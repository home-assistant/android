package io.homeassistant.companion.android.sensors

import android.content.Context
import android.util.Log
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.database.AppDatabase
import io.homeassistant.companion.android.database.sensor.Setting

class LastUpdateManager : SensorManager {
    companion object {
        private const val TAG = "LastUpdate"
        private const val ADD_NEW_INTENT = "Add New Intent"

        val lastUpdate = SensorManager.BasicSensor(
            "last_update",
            "sensor",
            R.string.basic_sensor_name_last_update,
            R.string.sensor_description_last_update
        )
    }

    override fun docsLink(): String {
        return "https://companion.home-assistant.io/docs/core/sensors#last-update-trigger-sensor"
    }
    override val enabledByDefault: Boolean
        get() = false
    override val name: Int
        get() = R.string.sensor_name_last_update

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

        val icon = "mdi:update"

        Log.d(TAG, "Last update is $intentAction")

        onSensorUpdated(context,
            lastUpdate,
            intentAction,
            icon,
            mapOf()
        )
        val sensorDao = AppDatabase.getInstance(context).sensorDao()
        val allSettings = sensorDao.getSettings(lastUpdate.id)
        val addNewIntent = allSettings.firstOrNull { it.name == ADD_NEW_INTENT }?.value ?: "false"
        val intentSetting = allSettings.firstOrNull { it.name == "intent" + allSettings.size }?.value ?: ""
        if (addNewIntent == "true") {
            if (intentSetting == "") {
                sensorDao.add(Setting(lastUpdate.id, ADD_NEW_INTENT, "false", "toggle"))
                sensorDao.add(Setting(lastUpdate.id, "intent" + allSettings.size, intentAction, "string"))
            }
        } else {
            sensorDao.add(Setting(lastUpdate.id, ADD_NEW_INTENT, "false", "toggle"))
        }
        for (setting in allSettings) {
            if (setting.value == "")
                sensorDao.removeSetting(lastUpdate.id, setting.name)
        }
    }
}
