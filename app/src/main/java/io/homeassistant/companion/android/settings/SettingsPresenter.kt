package io.homeassistant.companion.android.settings

import androidx.preference.PreferenceDataStore
import io.homeassistant.companion.android.common.data.integration.impl.entities.RateLimitResponse

interface SettingsPresenter {
    fun init(settingsView: SettingsView)
    fun getPreferenceDataStore(): PreferenceDataStore
    fun onCreate()
    fun onFinish()
    fun isLockEnabled(): Boolean
    fun sessionTimeOut(): Int
    suspend fun getNotificationRateLimits(): RateLimitResponse?

    fun setSessionExpireMillis(value: Long)
    fun getSessionExpireMillis(): Long
    fun isSsidUsed(): Boolean
    fun clearSsids()
}
