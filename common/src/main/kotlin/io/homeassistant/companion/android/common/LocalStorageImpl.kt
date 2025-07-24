package io.homeassistant.companion.android.common

import android.content.SharedPreferences
import androidx.core.content.edit
import io.homeassistant.companion.android.common.data.LocalStorage

/**
 * This class is simply used to load the shared preferences in a suspendable way and only once.
 */
private class SharedPreferenceRetriever(private val sharedPreferencesProvider: suspend () -> SharedPreferences) {
    private val sharedPreferences: SharedPreferences? = null

    suspend operator fun invoke(): SharedPreferences {
        return sharedPreferences ?: sharedPreferencesProvider()
    }
}

class LocalStorageImpl(sharedPreferences: suspend () -> SharedPreferences) : LocalStorage {
    private val sharedPreferences = SharedPreferenceRetriever(sharedPreferences)

    override suspend fun putString(key: String, value: String?) {
        sharedPreferences().edit { putString(key, value) }
    }

    override suspend fun getString(key: String): String? {
        return sharedPreferences().getString(key, null)
    }

    override suspend fun putLong(key: String, value: Long?) {
        sharedPreferences().edit {
            value?.let { putLong(key, value) } ?: remove(key)
        }
    }

    override suspend fun getLong(key: String): Long? {
        return if (sharedPreferences().contains(key)) {
            sharedPreferences().getLong(key, 0)
        } else {
            null
        }
    }

    override suspend fun putInt(key: String, value: Int?) {
        sharedPreferences().edit {
            value?.let { putInt(key, value) } ?: remove(key)
        }
    }

    override suspend fun getInt(key: String): Int? {
        return if (sharedPreferences().contains(key)) {
            sharedPreferences().getInt(key, 0)
        } else {
            null
        }
    }

    override suspend fun putBoolean(key: String, value: Boolean) {
        sharedPreferences().edit { putBoolean(key, value) }
    }

    override suspend fun getBoolean(key: String): Boolean {
        return sharedPreferences().getBoolean(key, false)
    }

    override suspend fun getBooleanOrNull(key: String): Boolean? = if (sharedPreferences().contains(key)) {
        sharedPreferences().getBoolean(key, false)
    } else {
        null
    }

    override suspend fun putStringSet(key: String, value: Set<String>) {
        sharedPreferences().edit { putStringSet(key, value) }
    }

    override suspend fun getStringSet(key: String): Set<String>? {
        return sharedPreferences().getStringSet(key, null)
    }

    override suspend fun remove(key: String) {
        sharedPreferences().edit { remove(key) }
    }
}
