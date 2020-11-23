package io.homeassistant.companion.android.settings

import androidx.preference.PreferenceDataStore
import io.homeassistant.companion.android.common.data.integration.impl.entities.RateLimitResponse

interface SettingsPresenter {
    fun getPreferenceDataStore(): PreferenceDataStore
    fun onCreate()
    fun onFinish()
    fun nfcEnabled(): Boolean
    fun isLockEnabled(): Boolean
    fun sessionTimeOut(): Int
    fun getNotificationRateLimits(): RateLimitResponse?

    fun setSessionExpireMillis(value: Long)
    fun getSessionExpireMillis(): Long
    fun isSsidUsed(): Boolean
    fun clearSsids()
}
