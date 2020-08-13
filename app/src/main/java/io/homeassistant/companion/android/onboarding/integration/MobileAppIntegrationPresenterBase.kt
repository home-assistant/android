package io.homeassistant.companion.android.onboarding.integration

import android.os.Build
import android.util.Log
import io.homeassistant.companion.android.BuildConfig
import io.homeassistant.companion.android.domain.integration.DeviceRegistration
import io.homeassistant.companion.android.domain.integration.IntegrationUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

open class MobileAppIntegrationPresenterBase constructor(
    private val view: MobileAppIntegrationView,
    private val integrationUseCase: IntegrationUseCase
) : MobileAppIntegrationPresenter {

    companion object {
        internal const val TAG = "IntegrationPresenter"
    }

    private val mainScope: CoroutineScope = CoroutineScope(Dispatchers.Main + Job())

    internal open suspend fun createRegistration(simple: Boolean): DeviceRegistration {
        return DeviceRegistration(
            "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
            Build.MODEL ?: "UNKNOWN"
        )
    }

    override fun onRegistrationAttempt(simple: Boolean) {
        view.showLoading()
        mainScope.launch {
            val deviceRegistration: DeviceRegistration
            try {
                deviceRegistration = createRegistration(simple)
            } catch (e: Exception) {
                Log.e(TAG, "Unable to create registration.", e)
                view.showError(true)
                return@launch
            }
            try {
                integrationUseCase.registerDevice(deviceRegistration)
            } catch (e: Exception) {
                Log.e(TAG, "Unable to register with Home Assistant", e)
                view.showError(false)
                return@launch
            }
            view.deviceRegistered()
        }
    }

    override fun onFinish() {
        mainScope.cancel()
    }
}
