package io.homeassistant.companion.android.settings

import android.content.Context
import androidx.preference.PreferenceDataStore
import io.homeassistant.companion.android.common.data.integration.impl.entities.RateLimitResponse
import io.homeassistant.companion.android.database.server.Server
import io.homeassistant.companion.android.onboarding.OnboardApp
import kotlinx.coroutines.flow.StateFlow

interface SettingsPresenter {
    fun init(view: SettingsView)
    fun getPreferenceDataStore(): PreferenceDataStore
    fun onFinish()
    suspend fun addServer(result: OnboardApp.Output?)
    fun getServersFlow(): StateFlow<List<Server>>
    fun getServerCount(): Int
    suspend fun getNotificationRateLimits(): RateLimitResponse?
    fun showChangeLog(context: Context)
}
