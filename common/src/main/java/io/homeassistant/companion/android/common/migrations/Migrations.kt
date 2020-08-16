package io.homeassistant.companion.android.common.migrations

import android.app.Application
import android.content.Context
import android.util.Log

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
        var version = preferences.getInt(PREF_VERSION, LATEST_VERSION)

        if(version < 3) {
            migration3()
        }

        preferences.edit().putInt(PREF_VERSION, LATEST_VERSION).apply()
    }

    /**
     * Migrate to use the string set for multiple ssids and remove the old ssid.
     */
    private fun migration2() {
        Log.i(TAG, "Starting Migration #2")
        val url = application.getSharedPreferences("url_0", Context.MODE_PRIVATE)

        val oldSsid = url.getString("wifi_ssid", null)
        if (!oldSsid.isNullOrBlank()) {
            url.edit()
                .putStringSet("wifi_ssids", setOf(oldSsid))
                .remove("wifi_ssid")
                .apply()
        }

        Log.i(TAG, "Completed Migration #2")
    }

    /**
     * "Migrate" to the new room db for saving settings.  Hopefully the new icons are enough to
     * look over the fact they had to setup widgets again...
     */
    private fun migration3(){
        val widgetLocalStorage = application.getSharedPreferences("widget", Context.MODE_PRIVATE)
        widgetLocalStorage.edit().clear().apply()
    }
}
