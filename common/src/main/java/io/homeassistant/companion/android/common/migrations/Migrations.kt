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
    }

    fun migrate() {
        val preferences = application.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        var version = preferences.getInt(PREF_VERSION, 0)

        if (version < 1) {
            migration1()
            version = 1
        }
        if (version < 2) {
            migration2()
            version = 2
        }

        preferences.edit().putInt(PREF_VERSION, version).apply()
    }

    /**
     * Migrate to url repository and multiple prepare for multiple instances
     */
    private fun migration1() {
        Log.i(TAG, "Starting Migration #1")
        val auth = application.getSharedPreferences("session", Context.MODE_PRIVATE)
        val integration = application.getSharedPreferences("integration", Context.MODE_PRIVATE)

        val url = application.getSharedPreferences("url_0", Context.MODE_PRIVATE)

        url.edit()
            .putString("cloudhook_url", integration.getString("cloud_url", null))
            .putString("remote_url", integration.getString("remote_ui_url", auth.getString("url", null)))
            .putString("webhook_id", integration.getString("webhook_id", null))
            .apply()

        val newAuth = application.getSharedPreferences("session_0", Context.MODE_PRIVATE)
        newAuth.edit()
            .putString("access_token", auth.getString("access_token", null))
            .putLong("expires_date", auth.getLong("expires_date", 0))
            .putString("refresh_token", auth.getString("refresh_token", null))
            .putString("token_type", auth.getString("token_type", null))
            .apply()

        val newIntegration = application.getSharedPreferences("integration_0", Context.MODE_PRIVATE)
        newIntegration.edit()
            .putString("app_version", integration.getString("app_version", null))
            .putString("device_name", integration.getString("device_name", null))
            .putString("push_token", integration.getString("push_token", null))
            .putString("secret", integration.getString("secret", null))
            .putBoolean("zone_enabled", integration.getBoolean("zone_enabled", false))
            .putBoolean("background_enabled", integration.getBoolean("background_enabled", false))
            .apply()

        Log.i(TAG, "Completed Migration #1")
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
}
