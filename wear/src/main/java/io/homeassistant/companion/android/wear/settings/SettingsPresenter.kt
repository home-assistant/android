package io.homeassistant.companion.android.wear.settings

import androidx.preference.PreferenceDataStore

interface SettingsPresenter {
    fun onViewReady()
    fun dataStore(): PreferenceDataStore
    fun syncSettings()
    fun finish()
}