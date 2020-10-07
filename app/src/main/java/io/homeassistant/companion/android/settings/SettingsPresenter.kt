package io.homeassistant.companion.android.settings

import androidx.preference.PreferenceDataStore

interface SettingsPresenter {
    fun getPreferenceDataStore(): PreferenceDataStore
    fun onCreate()
    fun onFinish()
    fun nfcEnabled(): Boolean
    fun isLockEnabled(): Boolean
    fun sessionTimeOut(): Int
    fun getNotificationRateLimits(): HashMap<String, *>?

    fun setSessionExpireMillis(value: Long)
    fun getSessionExpireMillis(): Long
}
