package io.homeassistant.companion.android.settings.developer

import android.content.Context
import androidx.activity.result.ActivityResult
import androidx.preference.PreferenceDataStore

interface DeveloperSettingsPresenter {
    fun init(view: DeveloperSettingsView)
    fun getPreferenceDataStore(): PreferenceDataStore
    fun onFinish()

    fun hasMultipleServers(): Boolean

    fun appSupportsThread(): Boolean
    fun runThreadDebug(context: Context, serverId: Int)
    fun onThreadPermissionResult(context: Context, result: ActivityResult, serverId: Int, isDeviceOnly: Boolean)

    fun webViewSupportsClearCache(): Boolean
    fun clearWebViewCache()
}
