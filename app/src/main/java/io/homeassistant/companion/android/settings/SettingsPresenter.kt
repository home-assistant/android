package io.homeassistant.companion.android.settings

import android.content.Context
import androidx.preference.PreferenceDataStore
import io.homeassistant.companion.android.common.data.integration.impl.entities.RateLimitResponse
import io.homeassistant.companion.android.database.server.Server
import io.homeassistant.companion.android.onboarding.OnboardApp

interface SettingsPresenter {
    fun getPreferenceDataStore(): PreferenceDataStore
    fun onFinish()
    fun getServerCount(): Int
    suspend fun addServer(result: OnboardApp.Output?)
    fun getServers(): List<Server>
    suspend fun getNotificationRateLimits(): RateLimitResponse?
    fun showChangeLog(context: Context)
}
