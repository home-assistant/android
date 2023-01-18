package io.homeassistant.companion.android.settings

import android.content.Context
import androidx.preference.PreferenceDataStore
import io.homeassistant.companion.android.common.data.integration.impl.entities.RateLimitResponse

interface SettingsPresenter {
    fun getPreferenceDataStore(): PreferenceDataStore
    fun onFinish()
    fun getServerName(): String
    suspend fun getNotificationRateLimits(): RateLimitResponse?
    fun showChangeLog(context: Context)
}
