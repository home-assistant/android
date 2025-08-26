package io.homeassistant.companion.android.common

import android.content.SharedPreferences
import androidx.core.content.edit
import io.homeassistant.companion.android.common.data.LocalStorage
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * This class is simply used to load the shared preferences in a suspendable way and only once.
 */
private class SharedPreferenceRetriever(private val sharedPreferencesProvider: suspend () -> SharedPreferences) {
    private val mutex = Mutex()
    private val sharedPreferences = AtomicReference<SharedPreferences?>(null)

    suspend operator fun invoke(): SharedPreferences {
        mutex.withLock {
            sharedPreferences.compareAndSet(null, sharedPreferencesProvider())
        }
        return checkNotNull(sharedPreferences.get()) { "SharedPreferences not initialized" }
    }
}

/**
 * Even if it most of the time ok to invoke [SharedPreferences] functions from the main thread, we should avoid
 * especially on the first call, since the system will need to read the data from the disk before loading it in RAM.
 *
 * This class makes sure that all the function are made from an IO coroutine context, and that we only get the
 * [SharedPreferences] only once in the lifecycle of this class.
 */
class LocalStorageImpl(sharedPreferences: suspend () -> SharedPreferences) : LocalStorage {
    private val sharedPreferences = SharedPreferenceRetriever(sharedPreferences)

    override suspend fun putString(key: String, value: String?) {
        withContext(Dispatchers.IO) { sharedPreferences().edit { putString(key, value) } }
    }

    override suspend fun getString(key: String): String? {
        return withContext(Dispatchers.IO) { sharedPreferences().getString(key, null) }
    }

    override suspend fun putLong(key: String, value: Long?) {
        withContext(Dispatchers.IO) {
            sharedPreferences().edit {
                value?.let { putLong(key, value) } ?: remove(key)
            }
        }
    }

    override suspend fun getLong(key: String): Long? {
        return withContext(Dispatchers.IO) {
            if (sharedPreferences().contains(key)) {
                sharedPreferences().getLong(key, 0)
            } else {
                null
            }
        }
    }

    override suspend fun putInt(key: String, value: Int?) {
        withContext(Dispatchers.IO) {
            sharedPreferences().edit {
                value?.let { putInt(key, value) } ?: remove(key)
            }
        }
    }

    override suspend fun getInt(key: String): Int? {
        return withContext(Dispatchers.IO) {
            if (sharedPreferences().contains(key)) {
                sharedPreferences().getInt(key, 0)
            } else {
                null
            }
        }
    }

    override suspend fun putBoolean(key: String, value: Boolean) {
        withContext(Dispatchers.IO) { sharedPreferences().edit { putBoolean(key, value) } }
    }

    override suspend fun getBoolean(key: String): Boolean {
        return withContext(Dispatchers.IO) { sharedPreferences().getBoolean(key, false) }
    }

    override suspend fun getBooleanOrNull(key: String): Boolean? = withContext(Dispatchers.IO) {
        if (sharedPreferences().contains(key)) {
            sharedPreferences().getBoolean(key, false)
        } else {
            null
        }
    }

    override suspend fun putStringSet(key: String, value: Set<String>) {
        withContext(Dispatchers.IO) { sharedPreferences().edit { putStringSet(key, value) } }
    }

    override suspend fun getStringSet(key: String): Set<String>? {
        return withContext(Dispatchers.IO) { sharedPreferences().getStringSet(key, null) }
    }

    override suspend fun remove(key: String) {
        withContext(Dispatchers.IO) { sharedPreferences().edit { remove(key) } }
    }
}
