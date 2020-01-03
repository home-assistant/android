package io.homeassistant.companion.android.common.migrations

import android.app.Application
import android.content.Context
import android.util.Log

class Migrations constructor(
    private val application: Application
){
    companion object {
        private const val TAG = "Migrations"
        private const val PREF_NAME = "migrations"
        private const val PREF_VERSION = "migration_version"
    }

    fun migrate(){
        val preferences = application.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        var version = preferences.getInt(PREF_VERSION, 0)
        if (version < 1) {
            migration1()
            version++
        }

        preferences.edit().putInt(PREF_VERSION, version).apply()
    }

    /**
     * Migrate to url repository and multiple prepare for multiple instances
     */
    private fun migration1(){
        Log.i(TAG, "Starting Migration #1")
        val auth = application.getSharedPreferences("session", Context.MODE_PRIVATE)
        val integration = application.getSharedPreferences("integration", Context.MODE_PRIVATE)

        val url = application.getSharedPreferences("url_0", Context.MODE_PRIVATE)

        url.edit()
            .putString("cloudhook_url", integration.getString("cloud_url", null))
            .putString("remote_url", integration.getString("remote_ui_url", null))
            .putString("webhook_id", integration.getString("webhook_id", null))
            .putString("local_url", auth.getString("url", null))
            .apply()

        Log.i(TAG, "Completed Migration #1")
    }
}