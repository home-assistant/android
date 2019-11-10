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

}
