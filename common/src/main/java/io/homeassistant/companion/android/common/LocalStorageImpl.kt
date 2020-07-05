package io.homeassistant.companion.android.common

import android.content.SharedPreferences
import io.homeassistant.companion.android.data.LocalStorage

class LocalStorageImpl(private val sharedPreferences: SharedPreferences) : LocalStorage {

    override suspend fun putString(key: String, value: String?) {
        sharedPreferences.edit().putString(key, value).apply()
    }

    override suspend fun getString(key: String): String? {
        return sharedPreferences.getString(key, null)
    }

    override suspend fun putLong(key: String, value: Long?) {
        if (value == null) {
            sharedPreferences.edit().remove(key).apply()
        } else {
            sharedPreferences.edit().putLong(key, value).apply()
        }
    }

    override suspend fun getLong(key: String): Long? {
        return if (sharedPreferences.contains(key)) {
            sharedPreferences.getLong(key, 0)
        } else {
            null
        }
    }

    override suspend fun putInt(key: String, value: Int?) {
        if (value == null) {
            sharedPreferences.edit().remove(key).apply()
        } else {
            sharedPreferences.edit().putInt(key, value).apply()
        }
    }

    override suspend fun getInt(key: String): Int? {
        return if (sharedPreferences.contains(key)) {
            sharedPreferences.getInt(key, 0)
        } else {
            null
        }
    }

    override suspend fun putBoolean(key: String, value: Boolean) {
        sharedPreferences.edit().putBoolean(key, value).apply()
    }

    override suspend fun getBoolean(key: String): Boolean {
        return sharedPreferences.getBoolean(key, false)
    }

    override suspend fun putStringSet(key: String, value: Set<String>) {
        sharedPreferences.edit().putStringSet(key, value).apply()
    }

    override suspend fun getStringSet(key: String): Set<String>? {
        return sharedPreferences.getStringSet(key, null)
    }

    override suspend fun remove(key: String) {
        sharedPreferences.edit().remove(key).apply()
    }
}
