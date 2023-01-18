package io.homeassistant.companion.android.settings

import android.content.Context
import android.util.Log
import androidx.preference.PreferenceDataStore
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.common.data.integration.impl.entities.RateLimitResponse
import io.homeassistant.companion.android.common.data.prefs.PrefsRepository
import io.homeassistant.companion.android.common.data.url.UrlRepository
import io.homeassistant.companion.android.settings.language.LanguagesManager
import io.homeassistant.companion.android.themes.ThemesManager
import io.homeassistant.companion.android.util.ChangeLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import javax.inject.Inject

class SettingsPresenterImpl @Inject constructor(
    private val urlUseCase: UrlRepository,
    private val integrationUseCase: IntegrationRepository,
    private val prefsRepository: PrefsRepository,
    private val themesManager: ThemesManager,
    private val langsManager: LanguagesManager,
    private val changeLog: ChangeLog
) : SettingsPresenter, PreferenceDataStore() {

    companion object {
        private const val TAG = "SettingsPresenter"
    }

    private val mainScope: CoroutineScope = CoroutineScope(Dispatchers.Main + Job())

    override fun getBoolean(key: String, defValue: Boolean): Boolean = runBlocking {
        return@runBlocking when (key) {
            "fullscreen" -> prefsRepository.isFullScreenEnabled()
            "keep_screen_on" -> prefsRepository.isKeepScreenOnEnabled()
            "pinch_to_zoom" -> prefsRepository.isPinchToZoomEnabled()
            "crash_reporting" -> prefsRepository.isCrashReporting()
            "autoplay_video" -> prefsRepository.isAutoPlayVideoEnabled()
            "always_show_first_view_on_app_start" -> prefsRepository.isAlwaysShowFirstViewOnAppStartEnabled()
            "webview_debug" -> prefsRepository.isWebViewDebugEnabled()
            else -> throw IllegalArgumentException("No boolean found by this key: $key")
        }
    }

    override fun putBoolean(key: String, value: Boolean) {
        mainScope.launch {
            when (key) {
                "fullscreen" -> prefsRepository.setFullScreenEnabled(value)
                "keep_screen_on" -> prefsRepository.setKeepScreenOnEnabled(value)
                "pinch_to_zoom" -> prefsRepository.setPinchToZoomEnabled(value)
                "crash_reporting" -> prefsRepository.setCrashReporting(value)
                "autoplay_video" -> prefsRepository.setAutoPlayVideo(value)
                "always_show_first_view_on_app_start" -> prefsRepository.setAlwaysShowFirstViewOnAppStart(value)
                "webview_debug" -> prefsRepository.setWebViewDebugEnabled(value)
                else -> throw IllegalArgumentException("No boolean found by this key: $key")
            }
        }
    }

    override fun getString(key: String, defValue: String?): String? = runBlocking {
        when (key) {
            "themes" -> themesManager.getCurrentTheme()
            "languages" -> langsManager.getCurrentLang()
            else -> throw IllegalArgumentException("No string found by this key: $key")
        }
    }

    override fun putString(key: String, value: String?) {
        mainScope.launch {
            when (key) {
                "themes" -> themesManager.saveTheme(value)
                "languages" -> langsManager.saveLang(value)
                else -> throw IllegalArgumentException("No string found by this key: $key")
            }
        }
    }

    override fun getPreferenceDataStore(): PreferenceDataStore {
        return this
    }

    override fun onFinish() {
        mainScope.cancel()
    }

    override fun getServerRegistrationName(): String? = runBlocking {
        integrationUseCase.getRegistration().deviceName
    }

    override fun getServerName(): String = runBlocking {
        urlUseCase.getUrl()?.toString() ?: ""
    }

    override suspend fun getNotificationRateLimits(): RateLimitResponse? = withContext(Dispatchers.IO) {
        try {
            integrationUseCase.getNotificationRateLimits()
        } catch (e: Exception) {
            Log.d(TAG, "Unable to get rate limits")
            return@withContext null
        }
    }

    override fun showChangeLog(context: Context) {
        changeLog.showChangeLog(context, true)
    }
}
