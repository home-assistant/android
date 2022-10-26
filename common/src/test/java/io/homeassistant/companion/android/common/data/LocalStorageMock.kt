package io.homeassistant.companion.android.common.data

class LocalStorageMock : LocalStorage {
    private val data = mutableMapOf<String, Any?>()

    private fun put(key: String, value: Any?) {
        data[key] = value
    }

    override suspend fun putString(key: String, value: String?) = put(key, value)
    override suspend fun getString(key: String): String? = data[key] as String?
    override suspend fun putLong(key: String, value: Long?) = put(key, value)
    override suspend fun getLong(key: String): Long? = data[key] as Long?
    override suspend fun putInt(key: String, value: Int?) = put(key, value)
    override suspend fun getInt(key: String): Int? = data[key] as Int?
    override suspend fun putBoolean(key: String, value: Boolean) = put(key, value)
    override suspend fun getBoolean(key: String) = getBooleanOrNull(key) ?: false
    override suspend fun getBooleanOrNull(key: String) = data[key] as Boolean?

    override suspend fun putStringSet(key: String, value: Set<String>) = put(key, value)
    override suspend fun getStringSet(key: String): Set<String>? = data[key] as Set<String>?

    override suspend fun remove(key: String) {
        data.remove(key)
    }
}
