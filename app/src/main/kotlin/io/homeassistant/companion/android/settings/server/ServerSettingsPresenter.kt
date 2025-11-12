package io.homeassistant.companion.android.settings.server

import androidx.preference.PreferenceDataStore

interface ServerSettingsPresenter {
    fun init(view: ServerSettingsView, serverId: Int)
    fun getPreferenceDataStore(): PreferenceDataStore
    suspend fun deleteServer()
    fun onFinish()

    fun hasMultipleServers(): Boolean
    fun updateServerName()
    fun updateUrlStatus()
    fun hasWifi(): Boolean
    fun clearSsids()

    fun setAppActive(active: Boolean)

    suspend fun serverURL(): String?

    suspend fun getAllowInsecureConnection(): Boolean?
}
