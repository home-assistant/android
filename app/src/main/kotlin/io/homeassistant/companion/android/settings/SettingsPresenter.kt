package io.homeassistant.companion.android.settings

import android.content.Context
import androidx.preference.PreferenceDataStore
import io.homeassistant.companion.android.common.data.integration.impl.entities.RateLimitResponse
import io.homeassistant.companion.android.database.server.Server
import io.homeassistant.companion.android.onboarding.OnboardApp
import kotlinx.coroutines.flow.StateFlow

interface SettingsPresenter {
    companion object {
        const val SUGGESTION_ASSISTANT_APP = "assistant_app"
        const val SUGGESTION_NOTIFICATION_PERMISSION = "notification_permission"
    }

    fun init(view: SettingsView)
    fun getPreferenceDataStore(): PreferenceDataStore
    fun onFinish()
    fun updateSuggestions(context: Context)
    fun cancelSuggestion(context: Context, id: String)
    suspend fun addServer(result: OnboardApp.Output?)
    fun getSuggestionFlow(): StateFlow<SettingsHomeSuggestion?>
    fun getServersFlow(): StateFlow<List<Server>>
    fun getServerCount(): Int
    suspend fun getNotificationRateLimits(): RateLimitResponse?
    suspend fun showChangeLog(context: Context)
    suspend fun isChangeLogPopupEnabled(): Boolean
    suspend fun setChangeLogPopupEnabled(enabled: Boolean)
}
