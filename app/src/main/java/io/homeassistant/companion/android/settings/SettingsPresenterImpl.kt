package io.homeassistant.companion.android.settings

import android.util.Log
import androidx.preference.PreferenceDataStore
import io.homeassistant.companion.android.domain.integration.IntegrationUseCase
import io.homeassistant.companion.android.domain.integration.Panel
import io.homeassistant.companion.android.domain.url.UrlUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

class SettingsPresenterImpl @Inject constructor(
    private val settingsView: SettingsView,
    private val dataStore: SettingsPreferenceDataStore,
    private val urlUseCase: UrlUseCase,
    private val integrationUseCase: IntegrationUseCase
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
        dataStore.changeCallback = this
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

    override fun getPanels(): Array<Panel> {
        return runBlocking {
            var panels = arrayOf<Panel>()
            try {
                panels = integrationUseCase.getPanels()
            } catch (e: Exception) {
                Log.e("SettingsPresenterImpl", "Issue getting panels.", e)
            }
            panels
        }
    }

    override fun onFinish() {
        dataStore.cancel()
        mainScope.cancel()
    }
}
