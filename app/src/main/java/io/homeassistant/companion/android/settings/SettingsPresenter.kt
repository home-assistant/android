package io.homeassistant.companion.android.settings

import androidx.preference.PreferenceDataStore

interface SettingsPresenter {
    fun getPreferenceDataStore(): PreferenceDataStore
    fun onFinish()
}
