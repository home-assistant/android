package io.homeassistant.companion.android.settings

import android.util.Log
import androidx.preference.PreferenceDataStore
import io.homeassistant.companion.android.domain.authentication.AuthenticationUseCase
import io.homeassistant.companion.android.domain.integration.IntegrationUseCase
import io.homeassistant.companion.android.domain.integration.Panel
import io.homeassistant.companion.android.domain.url.UrlUseCase
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class SettingsPresenterImpl @Inject constructor(
    private val settingsView: SettingsView,
    private val urlUseCase: UrlUseCase,
    private val integrationUseCase: IntegrationUseCase,
    private val authenticationUseCase: AuthenticationUseCase
) : SettingsPresenter, PreferenceDataStore() {

    companion object {
        private const val TAG = "SettingsPresenter"
    }

    private val mainScope: CoroutineScope = CoroutineScope(Dispatchers.Main + Job())

    override fun getBoolean(key: String, defValue: Boolean): Boolean {
        return runBlocking {
            return@runBlocking when (key) {
                "location_zone" -> integrationUseCase.isZoneTrackingEnabled()
                "location_background" -> integrationUseCase.isBackgroundTrackingEnabled()
                "fullscreen" -> integrationUseCase.isFullScreenEnabled()
                "app_lock" -> authenticationUseCase.isLockEnabled()
                else -> throw IllegalArgumentException("No boolean found by this key: $key")
            }
        }
    }

    override fun putBoolean(key: String, value: Boolean) {
        mainScope.launch {
            when (key) {
                "location_zone" -> integrationUseCase.setZoneTrackingEnabled(value)
                "location_background" -> integrationUseCase.setBackgroundTrackingEnabled(value)
                "fullscreen" -> integrationUseCase.setFullScreenEnabled(value)
                "app_lock" -> authenticationUseCase.setLockEnabled(value)
                else -> throw IllegalArgumentException("No boolean found by this key: $key")
            }
            if (key == "location_zone" || key == "location_background")
                settingsView.onLocationSettingChanged()
        }
    }

    override fun getString(key: String, defValue: String?): String? {
        return runBlocking {
            when (key) {
                "connection_internal" -> (urlUseCase.getUrl(true) ?: "").toString()
                "connection_external" -> (urlUseCase.getUrl(false) ?: "").toString()
                "registration_name" -> integrationUseCase.getRegistration().deviceName
                "session_timeout" -> integrationUseCase.getSessionTimeOut().toString()
                else -> throw IllegalArgumentException("No string found by this key: $key")
            }
        }
    }

    override fun putString(key: String, value: String?) {
        mainScope.launch {
            when (key) {
                "connection_internal" -> urlUseCase.saveUrl(value ?: "", true)
                "connection_external" -> urlUseCase.saveUrl(value ?: "", false)
                "session_timeout" -> integrationUseCase.sessionTimeOut(value.toString().toInt())
                "registration_name" -> {
                    try {
                        integrationUseCase.updateRegistration(deviceName = value!!)
                    } catch (e: Exception) {
                        Log.e(TAG, "Issue updating registration with new device name", e)
                    }
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

    override fun getPanels(): Array<Panel> {
        return runBlocking {
            var panels = arrayOf<Panel>()
            try {
                panels = integrationUseCase.getPanels()
            } catch (e: Exception) {
                Log.e(SettingsPresenterImpl.TAG, "Issue getting panels.", e)
            }
            panels
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
}
