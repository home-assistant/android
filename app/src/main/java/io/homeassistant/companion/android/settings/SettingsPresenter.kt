package io.homeassistant.companion.android.settings

import android.content.Context
import androidx.preference.PreferenceDataStore
import io.homeassistant.companion.android.common.data.integration.impl.entities.RateLimitResponse

interface SettingsPresenter {
    fun init(settingsView: SettingsView)
    fun getPreferenceDataStore(): PreferenceDataStore
    fun onCreate()
    fun onFinish()
    fun updateExternalUrlStatus()
    fun updateInternalUrlStatus()
    fun setAppActive(active: Boolean)
    suspend fun getNotificationRateLimits(): RateLimitResponse?

    fun isSsidUsed(): Boolean
    fun clearSsids()
    fun showChangeLog(context: Context)
}
