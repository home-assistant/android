package io.homeassistant.companion.android.settings.server

import android.util.Log
import androidx.preference.PreferenceDataStore
import io.homeassistant.companion.android.common.data.authentication.AuthenticationRepository
import io.homeassistant.companion.android.common.data.integration.DeviceRegistration
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.common.data.url.UrlRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

class ServerSettingsPresenterImpl @Inject constructor(
    private val authenticationRepository: AuthenticationRepository,
    private val integrationRepository: IntegrationRepository,
    private val urlRepository: UrlRepository
) : ServerSettingsPresenter, PreferenceDataStore() {

    companion object {
        private const val TAG = "ServerSettingsPresImpl"
    }

    private val mainScope: CoroutineScope = CoroutineScope(Dispatchers.Main + Job())
    private lateinit var view: ServerSettingsView

    override fun init(view: ServerSettingsView) {
        this.view = view
    }

    override fun getPreferenceDataStore(): PreferenceDataStore = this

    override fun getBoolean(key: String?, defValue: Boolean): Boolean = runBlocking {
        when (key) {
            "app_lock" -> authenticationRepository.isLockEnabledRaw()
            "app_lock_home_bypass" -> authenticationRepository.isLockHomeBypassEnabled()
            else -> throw IllegalArgumentException("No boolean found by this key: $key")
        }
    }

    override fun putBoolean(key: String?, value: Boolean) {
        mainScope.launch {
            when (key) {
                "app_lock" -> authenticationRepository.setLockEnabled(value)
                "app_lock_home_bypass" -> authenticationRepository.setLockHomeBypassEnabled(value)
                else -> throw IllegalArgumentException("No boolean found by this key: $key")
            }
        }
    }

    override fun getString(key: String?, defValue: String?): String? = runBlocking {
        when (key) {
            "connection_internal" -> (urlRepository.getUrl(isInternal = true, force = true) ?: "").toString()
            "registration_name" -> integrationRepository.getRegistration().deviceName
            "session_timeout" -> integrationRepository.getSessionTimeOut().toString()
            else -> throw IllegalArgumentException("No string found by this key: $key")
        }
    }

    override fun putString(key: String?, value: String?) {
        mainScope.launch {
            when (key) {
                "connection_internal" -> urlRepository.saveUrl(value ?: "", true)
                "session_timeout" -> {
                    try {
                        integrationRepository.sessionTimeOut(value.toString().toInt())
                    } catch (e: Exception) {
                        Log.e(TAG, "Issue saving session timeout value", e)
                    }
                }
                "registration_name" -> {
                    try {
                        integrationRepository.updateRegistration(DeviceRegistration(deviceName = value!!))
                    } catch (e: Exception) {
                        Log.e(TAG, "Issue updating registration with new device name", e)
                    }
                }
                else -> throw IllegalArgumentException("No string found by this key: $key")
            }
        }
    }

    override fun onFinish() {
        mainScope.cancel()
    }

    override fun updateUrlStatus() {
        mainScope.launch {
            view.updateExternalUrl(
                urlRepository.getUrl(false)?.toString() ?: "",
                urlRepository.shouldUseCloud() && urlRepository.canUseCloud()
            )
        }
        mainScope.launch {
            val ssids = urlRepository.getHomeWifiSsids()
            if (ssids.isEmpty()) urlRepository.saveUrl("", true)

            view.enableInternalConnection(ssids.isNotEmpty())
            view.updateSsids(ssids)
        }
    }

    override fun isSsidUsed(): Boolean = runBlocking {
        urlRepository.getHomeWifiSsids().isNotEmpty()
    }

    override fun clearSsids() {
        mainScope.launch {
            urlRepository.saveHomeWifiSsids(emptySet())
            updateUrlStatus()
        }
    }

    override fun setAppActive() = runBlocking {
        integrationRepository.setAppActive(true)
    }
}
