package io.homeassistant.companion.android.data

interface LocalStorage {

    suspend fun putString(key: String, value: String?)

    suspend fun getString(key: String): String?

    suspend fun putLong(key: String, value: Long?)

    suspend fun getLong(key: String): Long?
}
