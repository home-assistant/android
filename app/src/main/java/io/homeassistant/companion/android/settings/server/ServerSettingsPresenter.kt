package io.homeassistant.companion.android.settings.server

import androidx.preference.PreferenceDataStore

interface ServerSettingsPresenter {
    fun init(view: ServerSettingsView, serverId: Int)
    fun getPreferenceDataStore(): PreferenceDataStore
    fun onFinish()

    fun updateUrlStatus()
    fun isSsidUsed(): Boolean
    fun clearSsids()

    fun setAppActive()
}
