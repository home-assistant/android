package io.homeassistant.companion.android.migrations

import android.app.Application
import android.content.Context
import android.util.Log
import io.homeassistant.companion.android.database.AppDatabase
import io.homeassistant.companion.android.database.widget.ButtonWidgetEntity
import io.homeassistant.companion.android.database.widget.StaticWidgetEntity

class Migrations constructor(
    private val application: Application
) {
    companion object {
        private const val TAG = "Migrations"
        private const val PREF_NAME = "migrations"
        private const val PREF_VERSION = "migration_version"
        private const val LATEST_VERSION = 3
    }

    fun migrate() {
        val preferences = application.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val version = preferences.getInt(PREF_VERSION, LATEST_VERSION)

        if (version < 3) {
            migration3()
        }

        preferences.edit().putInt(PREF_VERSION, LATEST_VERSION).apply()
    }

    /**
     * "Migrate" to the new room db for saving settings.  Hopefully the new icons are enough to
     * look over the fact they had to setup widgets again...
     */
    private fun migration3() {
        Log.d(TAG, "Starting migration #3")
        val widgetLocalStorage = application.getSharedPreferences("widget", Context.MODE_PRIVATE)
        val buttonWidgetIds = widgetLocalStorage.all.keys
            .filter { it.startsWith("widget_icon") }
            .map { it.removePrefix("widget_icon") }
        val buttonWidgetDao = AppDatabase.getInstance(application).buttonWidgetDao()
        buttonWidgetIds.forEach { id ->
            val icon = widgetLocalStorage.getString("widget_icon$id", "ic_flash_on_24dp")
            val domain = widgetLocalStorage.getString("widget_domain$id", "")!!
            val service = widgetLocalStorage.getString("widget_service$id", "")!!
            val serviceData = widgetLocalStorage.getString("widget_serviceData$id", "")!!
            val label = widgetLocalStorage.getString("widget_label$id", null)

            val iconId = when (icon) {
                "ic_flash_on_24dp" -> 988171
                "ic_home_24dp" -> 62172
                "ic_lightbulb_outline_24dp" -> 62261
                "ic_power_settings_new_24dp" -> 62501
                else -> 988171
            }

            buttonWidgetDao.add(ButtonWidgetEntity(
                id.toInt(),
                iconId,
                domain,
                service,
                serviceData,
                label
            ))
        }

        val staticWidgetIds = widgetLocalStorage.all.keys
            .filter { it.startsWith("widget_entity") }
            .map { it.removePrefix("widget_entity") }

        val staticWidgetDao = AppDatabase.getInstance(application).staticWidgetDao()
        staticWidgetIds.forEach { id ->
            val entityId = widgetLocalStorage.getString("widget_entity$id", "")!!
            val attribute = widgetLocalStorage.getString("widget_attribute$id", null)
            val label = widgetLocalStorage.getString("widget_label$id", null)

            staticWidgetDao.add(StaticWidgetEntity(
                id.toInt(),
                entityId,
                attribute,
                label
            ))
        }

        widgetLocalStorage.edit().clear().apply()

        Log.d(TAG, "Finished migration #3")
    }
}
