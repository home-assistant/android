package io.homeassistant.companion.android.settings

import androidx.preference.PreferenceDataStore
import io.homeassistant.companion.android.domain.integration.IntegrationUseCase
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class SettingsPresenterImpl @Inject constructor(
    private val settingsView: SettingsView,
    private val integrationUseCase: IntegrationUseCase
) : SettingsPresenter, PreferenceDataStore() {

    private val mainScope: CoroutineScope = CoroutineScope(Dispatchers.Main + Job())

    override fun getBoolean(key: String?, defValue: Boolean): Boolean {
        return runBlocking {
            return@runBlocking when (key) {
                "location_zone" -> integrationUseCase.isZoneTrackingEnabled()
                "location_background" -> integrationUseCase.isBackgroundTrackingEnabled()
                else -> throw Exception()
            }
        }
    }

    override fun putBoolean(key: String?, value: Boolean) {
        mainScope.launch {
            when (key) {
                "location_zone" -> integrationUseCase.setZoneTrackingEnabled(value)
                "location_background" -> integrationUseCase.setBackgroundTrackingEnabled(value)
                else -> throw Exception()
            }
            settingsView.onLocationSettingChanged()
        }
    }

    override fun getPreferenceDataStore(): PreferenceDataStore {
        return this
    }

    override fun onFinish() {
        mainScope.cancel()
    }
}
