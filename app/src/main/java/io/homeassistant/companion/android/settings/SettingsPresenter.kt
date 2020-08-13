package io.homeassistant.companion.android.settings

import androidx.preference.PreferenceDataStore
import io.homeassistant.companion.android.domain.integration.Panel

interface SettingsPresenter {
    fun getPreferenceDataStore(): PreferenceDataStore
    fun onCreate()
    fun onFinish()
    fun nfcEnabled(): Boolean
    fun getPanels(): Array<Panel>
    fun isLockEnabled(): Boolean
    fun sessionTimeOut(): Int

    fun setSessionExpireMillis(value: Long)
    fun getSessionExpireMillis(): Long
}
