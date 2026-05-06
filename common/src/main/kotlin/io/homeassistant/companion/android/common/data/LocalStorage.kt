package io.homeassistant.companion.android.common.data

import kotlinx.coroutines.flow.Flow

interface LocalStorage {

    suspend fun putString(key: String, value: String?)

    suspend fun getString(key: String): String?

    suspend fun putLong(key: String, value: Long?)

    suspend fun getLong(key: String): Long?

    suspend fun putInt(key: String, value: Int?)

    suspend fun getInt(key: String): Int?

    suspend fun putBoolean(key: String, value: Boolean)

    suspend fun getBoolean(key: String): Boolean

    suspend fun getBooleanOrNull(key: String): Boolean?

    suspend fun putStringSet(key: String, value: Set<String>)

    suspend fun getStringSet(key: String): Set<String>?

    suspend fun remove(key: String)

    /**
     * Returns a [Flow] that emits the [key] each time the value associated with it changes
     * and only emits for the specified [key].
     */
    fun observeChanges(vararg keys: String): Flow<String>

    /**
     * Returns a [Flow] that emits the result of [mapper] each time the value associated with any
     * of the specified [keys] changes. The current mapped value is also emitted immediately upon
     * collection so collectors do not need to read the value separately before subscribing.
     *
     * [mapper] is invoked on every emission, including the initial one, and may suspend to read
     * from storage.
     */
    suspend fun <T> observeChanges(vararg keys: String, mapper: suspend () -> T): Flow<T>
}
