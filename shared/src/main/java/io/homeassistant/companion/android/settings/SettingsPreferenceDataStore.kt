package io.homeassistant.companion.android.settings

import androidx.preference.PreferenceDataStore
import io.homeassistant.companion.android.domain.authentication.AuthenticationUseCase
import io.homeassistant.companion.android.domain.integration.IntegrationUseCase
import io.homeassistant.companion.android.domain.url.UrlUseCase
import io.homeassistant.companion.android.util.extensions.catch
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class SettingsPreferenceDataStore @Inject constructor(
    private val integrationUseCase: IntegrationUseCase,
    private val authenticationUseCase: AuthenticationUseCase,
    private val urlUseCase: UrlUseCase
) : PreferenceDataStore() {

    private val ioScope = CoroutineScope(Dispatchers.IO)

    var changeCallback: PreferenceChangeCallback? = null

    override fun getBoolean(key: String, defValue: Boolean): Boolean {
        return runBlocking {
            return@runBlocking when (key) {
                "location_zone" -> integrationUseCase.isZoneTrackingEnabled()
                "location_background" -> integrationUseCase.isBackgroundTrackingEnabled()
                "fullscreen" -> integrationUseCase.isFullScreenEnabled()
                "app_lock" -> authenticationUseCase.isLockEnabled()
                "update_sensors" -> integrationUseCase.updateSensors()
                else -> throw IllegalArgumentException("No boolean found by this key: $key")
            }
        }
    }

    override fun putBoolean(key: String, value: Boolean) {
        ioScope.launch {
            when (key) {
                "location_zone" -> integrationUseCase.setZoneTrackingEnabled(value)
                "location_background" -> integrationUseCase.setBackgroundTrackingEnabled(value)
                "fullscreen" -> integrationUseCase.setFullScreenEnabled(value)
                "app_lock" -> authenticationUseCase.setLockEnabled(value)
                "update_sensors" -> integrationUseCase.setUpdateSensors(value)
                else -> throw IllegalArgumentException("No boolean found by this key: $key")
            }
            changeCallback?.onPreferenceChanged(key, value)
        }
    }

    override fun getString(key: String, defValue: String?): String? {
        return runBlocking {
            when (key) {
                "connection_internal" -> (urlUseCase.getUrl(true) ?: "").toString()
                "connection_external" -> (urlUseCase.getUrl(false) ?: "").toString()
                "registration_name" -> integrationUseCase.getRegistration().deviceName
                else -> throw IllegalArgumentException("No string found by this key: $key")
            }
        }
    }

    override fun putString(key: String, value: String?) {
        ioScope.launch {
            when (key) {
                "connection_internal" -> urlUseCase.saveUrl(value ?: "", true)
                "connection_external" -> urlUseCase.saveUrl(value ?: "", false)
                "registration_name" -> catch { integrationUseCase.updateRegistration(deviceName = value!!) }
                else -> throw IllegalArgumentException("No string found by this key: $key")
            }
            changeCallback?.onPreferenceChanged(key, value)
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
        ioScope.launch {
            when (key) {
                "connection_internal_ssids" -> urlUseCase.saveHomeWifiSsids(values ?: emptySet())
            }
            changeCallback?.onPreferenceChanged(key, values)
        }
    }

    fun cancel() {
        changeCallback = null
        ioScope.cancel()
    }
}
