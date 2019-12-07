package io.homeassistant.companion.android.settings

import io.homeassistant.companion.android.domain.integration.IntegrationUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

class SettingsPresenterImpl @Inject constructor(
    private val settingsView: SettingsView,
    private val integrationUseCase: IntegrationUseCase
) : SettingsPresenter {

    private val mainScope: CoroutineScope = CoroutineScope(Dispatchers.Main + Job())

    override fun onLocationZoneChange(value: Boolean) {
        mainScope.launch {
            integrationUseCase.setZoneTrackingEnabled(value)
            settingsView.onLocationSettingChanged()
        }
    }

    override fun onLocationBackgroundChange(value: Boolean) {
        mainScope.launch {
            integrationUseCase.setBackgroundTrackingEnabled(value)
            settingsView.onLocationSettingChanged()
        }
    }

    override fun onFinish() {
        mainScope.cancel()
    }
}
