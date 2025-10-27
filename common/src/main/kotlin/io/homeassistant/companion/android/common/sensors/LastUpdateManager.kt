package io.homeassistant.companion.android.common.sensors

import android.content.Context
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.database.AppDatabase
import io.homeassistant.companion.android.database.sensor.SensorSetting
import io.homeassistant.companion.android.database.sensor.SensorSettingType
import timber.log.Timber

class LastUpdateManager : SensorManager {
    companion object {
        private const val SETTING_ADD_NEW_INTENT = "lastupdate_add_new_intent"
        private const val INTENT_SETTING_PREFIX = "lastupdate_intent_var1:"

        val lastUpdate = SensorManager.BasicSensor(
            "last_update",
            "sensor",
            commonR.string.basic_sensor_name_last_update,
            commonR.string.sensor_description_last_update,
            "mdi:update",
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC,
            updateType = SensorManager.BasicSensor.UpdateType.INTENT,
        )
    }

    override fun docsLink(): String {
        return "https://companion.home-assistant.io/docs/core/sensors#last-update-trigger-sensor"
    }
    override val name: Int
        get() = commonR.string.sensor_name_last_update

    override suspend fun getAvailableSensors(context: Context): List<SensorManager.BasicSensor> {
        return listOf(lastUpdate)
    }

    override fun requiredPermissions(context: Context, sensorId: String): Array<String> {
        return emptyArray()
    }

    override suspend fun requestSensorUpdate(context: Context) {
        // No op
    }

    suspend fun sendLastUpdate(context: Context, intentAction: String?) {
        if (!isEnabled(context, lastUpdate)) {
            return
        }

        if (intentAction.isNullOrEmpty()) {
            return
        }

        Timber.d("Last update is $intentAction")

        onSensorUpdated(
            context,
            lastUpdate,
            intentAction,
            lastUpdate.statelessIcon,
            mapOf(),
        )

        val sensorDao = AppDatabase.getInstance(context).sensorDao()
        val (settingsToRemove, allSettings) = sensorDao.getSettings(lastUpdate.id).partition { setting ->
            setting.value.isEmpty()
        }
        if (settingsToRemove.isNotEmpty()) {
            sensorDao.removeSettings(lastUpdate.id, settingsToRemove.map { it.name })
        }
        val intentSettings = allSettings.filter {
            it.name.startsWith(INTENT_SETTING_PREFIX)
        }.sortedBy {
            it.name.removeSuffix(":").substringAfterLast(':').toInt()
        }
        val isSequenceContinuous = intentSettings.withIndex().all { (index, setting) ->
            val ordinal = setting.name.removePrefix(INTENT_SETTING_PREFIX).removeSuffix(":").toInt()
            ordinal == index + 1
        }
        if (!isSequenceContinuous) {
            // create new settings with sequential IDs:
            val newIntentSettings = intentSettings.mapIndexed { index, it ->
                it.copy(name = "$INTENT_SETTING_PREFIX${index + 1}:")
            }
            // delete old settings from DB:
            sensorDao.removeSettings(lastUpdate.id, intentSettings.map { it.name })
            // add new settings to DB:
            newIntentSettings.forEach {
                sensorDao.add(it)
            }
        }
        val addNewIntentToggle = allSettings.firstOrNull { it.name == SETTING_ADD_NEW_INTENT }
        if (addNewIntentToggle == null) {
            // add the toggle if it was not already added.
            sensorDao.add(SensorSetting(lastUpdate.id, SETTING_ADD_NEW_INTENT, "false", SensorSettingType.TOGGLE))
        } else if (addNewIntentToggle.value == "true") {
            val newIntentSettingOrdinal = intentSettings.size + 1
            val newIntentSettingName = "$INTENT_SETTING_PREFIX$newIntentSettingOrdinal:"
            if (allSettings.none { it.name == newIntentSettingName }) {
                // turn off the toggle:
                sensorDao.add(SensorSetting(lastUpdate.id, SETTING_ADD_NEW_INTENT, "false", SensorSettingType.TOGGLE))
                // add the new Intent:
                sensorDao.add(
                    SensorSetting(lastUpdate.id, newIntentSettingName, intentAction, SensorSettingType.STRING),
                )
            }
        }
    }
}
