package io.homeassistant.companion.android.data

interface LocalStorage {

    suspend fun putString(key: String, value: String?)

    suspend fun getString(key: String): String?

    suspend fun putLong(key: String, value: Long?)

    suspend fun getLong(key: String): Long?

    suspend fun putInt(key: String, value: Int?)

    suspend fun getInt(key: String): Int?

    suspend fun putBoolean(key: String, value: Boolean)

    suspend fun getBoolean(key: String): Boolean

    suspend fun putStringSet(key: String, value: Set<String>)

    suspend fun getStringSet(key: String): Set<String>?

    suspend fun remove(key: String)
}
