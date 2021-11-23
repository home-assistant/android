package io.homeassistant.companion.android.settings

import android.util.Log
import androidx.preference.PreferenceDataStore
import io.homeassistant.companion.android.common.data.authentication.AuthenticationRepository
import io.homeassistant.companion.android.common.data.integration.DeviceRegistration
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.common.data.integration.impl.entities.RateLimitResponse
import io.homeassistant.companion.android.common.data.prefs.PrefsRepository
import io.homeassistant.companion.android.common.data.url.UrlRepository
import io.homeassistant.companion.android.settings.language.LanguagesManager
import io.homeassistant.companion.android.themes.ThemesManager
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
    private val authenticationUseCase: AuthenticationRepository,
    private val prefsRepository: PrefsRepository,
    private val themesManager: ThemesManager,
    private val langsManager: LanguagesManager
) : SettingsPresenter, PreferenceDataStore() {

    companion object {
        private const val TAG = "SettingsPresenter"
    }

    private val mainScope: CoroutineScope = CoroutineScope(Dispatchers.Main + Job())
    private lateinit var settingsView: SettingsView

    override fun init(settingsView: SettingsView) {
        this.settingsView = settingsView
    }

    override fun getBoolean(key: String, defValue: Boolean): Boolean {
        return runBlocking {
            return@runBlocking when (key) {
                "fullscreen" -> integrationUseCase.isFullScreenEnabled()
                "keep_screen_on" -> integrationUseCase.isKeepScreenOnEnabled()
                "app_lock" -> authenticationUseCase.isLockEnabled()
                "crash_reporting" -> prefsRepository.isCrashReporting()
                "prioritize_internal" -> urlUseCase.isPrioritizeInternal()
                "autoplay_video" -> integrationUseCase.isAutoPlayVideoEnabled()
                else -> throw IllegalArgumentException("No boolean found by this key: $key")
            }
        }
    }

    override fun putBoolean(key: String, value: Boolean) {
        mainScope.launch {
            when (key) {
                "fullscreen" -> integrationUseCase.setFullScreenEnabled(value)
                "keep_screen_on" -> integrationUseCase.setKeepScreenOnEnabled(value)
                "app_lock" -> authenticationUseCase.setLockEnabled(value)
                "crash_reporting" -> prefsRepository.setCrashReporting(value)
                "prioritize_internal" -> urlUseCase.setPrioritizeInternal(value)
                "autoplay_video" -> integrationUseCase.setAutoPlayVideo(value)
                else -> throw IllegalArgumentException("No boolean found by this key: $key")
            }
        }
    }

    override fun getString(key: String, defValue: String?): String? {
        return runBlocking {
            when (key) {
                "connection_internal" -> (urlUseCase.getUrl(true) ?: "").toString()
                "connection_external" -> (urlUseCase.getUrl(false) ?: "").toString()
                "registration_name" -> integrationUseCase.getRegistration().deviceName
                "session_timeout" -> integrationUseCase.getSessionTimeOut().toString()
                "themes" -> themesManager.getCurrentTheme()
                "languages" -> langsManager.getCurrentLang()
                else -> throw IllegalArgumentException("No string found by this key: $key")
            }
        }
    }

    override fun putString(key: String, value: String?) {
        mainScope.launch {
            when (key) {
                "connection_internal" -> urlUseCase.saveUrl(value ?: "", true)
                "connection_external" -> urlUseCase.saveUrl(value ?: "", false)
                "session_timeout" -> {
                    try {
                        integrationUseCase.sessionTimeOut(value.toString().toInt())
                    } catch (e: Exception) {
                        Log.e(TAG, "Issue saving session timeout value", e)
                    }
                }
                "registration_name" -> {
                    try {
                        integrationUseCase.updateRegistration(DeviceRegistration(deviceName = value!!))
                    } catch (e: Exception) {
                        Log.e(TAG, "Issue updating registration with new device name", e)
                    }
                }
                "themes" -> themesManager.saveTheme(value)
                "languages" -> {
                    langsManager.saveLang(value)
                    settingsView.onLangSettingsChanged()
                }
                else -> throw IllegalArgumentException("No string found by this key: $key")
            }
        }
    }

    override fun getStringSet(key: String, defValues: Set<String>?): Set<String> {
        return runBlocking {
            when (key) {
                "connection_internal_ssids" -> urlUseCase.getHomeWifiSsids()
                else -> throw IllegalArgumentException("No stringSet found by this key: $key")
            }
        }
    }

    override fun putStringSet(key: String, values: Set<String>?) {
        mainScope.launch {
            when (key) {
                "connection_internal_ssids" -> {
                    val ssids = values ?: emptySet()
                    urlUseCase.saveHomeWifiSsids(ssids)
                    handleInternalUrlStatus(ssids)
                }
            }
        }
    }

    override fun getInt(key: String, defValue: Int): Int {
        return runBlocking {
            when (key) {
                "session_timeout" -> integrationUseCase.getSessionTimeOut()
                else -> throw IllegalArgumentException("No int found by this key: $key")
            }
        }
    }

    override fun putInt(key: String, value: Int) {
        mainScope.launch {
            when (key) {
                "session_timeout" -> integrationUseCase.sessionTimeOut(value)
                else -> throw IllegalArgumentException("No int found by this key: $key")
            }
        }
    }

    override fun getPreferenceDataStore(): PreferenceDataStore {
        return this
    }

    override fun onCreate() {
        mainScope.launch {
            handleInternalUrlStatus(urlUseCase.getHomeWifiSsids())
        }
    }

    override fun onFinish() {
        mainScope.cancel()
    }

    private suspend fun handleInternalUrlStatus(ssids: Set<String>) {
        if (ssids.isEmpty()) {
            settingsView.disableInternalConnection()
            urlUseCase.saveUrl("", true)
        } else {
            settingsView.enableInternalConnection()
        }
    }

    override fun isLockEnabled(): Boolean {
        return runBlocking {
            authenticationUseCase.isLockEnabled()
        }
    }

    override fun sessionTimeOut(): Int {
        return runBlocking {
            integrationUseCase.getSessionTimeOut()
        }
    }

    override fun setSessionExpireMillis(value: Long) {
        mainScope.launch {
            integrationUseCase.setSessionExpireMillis(value)
        }
    }

    override fun getSessionExpireMillis(): Long {
        return runBlocking {
            integrationUseCase.getSessionExpireMillis()
        }
    }

    override suspend fun getNotificationRateLimits(): RateLimitResponse? = withContext(Dispatchers.IO) {
        try {
            integrationUseCase.getNotificationRateLimits()
        } catch (e: Exception) {
            Log.d(TAG, "Unable to get rate limits")
            return@withContext null
        }
    }

    override fun clearSsids() {
        mainScope.launch {
            urlUseCase.saveHomeWifiSsids(emptySet())
        }
    }

    override fun isSsidUsed(): Boolean {
        return runBlocking {
            urlUseCase.getHomeWifiSsids().isNotEmpty()
        }
    }
}
