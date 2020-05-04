package io.homeassistant.companion.android.settings

import androidx.preference.PreferenceDataStore
import io.homeassistant.companion.android.domain.integration.Panel

interface SettingsPresenter {
    fun getPreferenceDataStore(): PreferenceDataStore
    fun onCreate()
    fun onFinish()
    fun getPanels(): Array<Panel>
}
