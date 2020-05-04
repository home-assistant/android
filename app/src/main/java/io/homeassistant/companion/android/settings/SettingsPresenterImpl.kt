package io.homeassistant.companion.android.settings

import androidx.preference.PreferenceDataStore
import io.homeassistant.companion.android.domain.url.UrlUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

class SettingsPresenterImpl @Inject constructor(
    private val settingsView: SettingsView,
    private val dataStore: SettingsPreferenceDataStore,
    private val urlUseCase: UrlUseCase
) : SettingsPresenter, PreferenceChangeCallback {

    private val mainScope: CoroutineScope = CoroutineScope(Dispatchers.Main + Job())

    @Suppress("UNCHECKED_CAST")
    override fun onPreferenceChanged(key: String, value: Any?) {
        when (key) {
            "location_zone",
            "location_background" -> settingsView.onLocationSettingChanged()
            "connection_internal_ssids" -> handleInternalUrlStatus(
                value as? Set<String> ?: emptySet()
            )
        }
    }

    override fun getPreferenceDataStore(): PreferenceDataStore {
        return dataStore
    }

    override fun onCreate() {
        mainScope.launch {
            handleInternalUrlStatus(urlUseCase.getHomeWifiSsids())
        }
    }

    private fun handleInternalUrlStatus(ssids: Set<String>) {
        if (ssids.isEmpty()) {
            settingsView.disableInternalConnection()
            mainScope.launch { urlUseCase.saveUrl("", true) }
        } else {
            settingsView.enableInternalConnection()
        }
    }

    override fun onFinish() {
        dataStore.cancel()
        mainScope.cancel()
    }
}
