package io.homeassistant.companion.android.onboarding.integration

import android.os.Build
import io.homeassistant.companion.android.BuildConfig
import io.homeassistant.companion.android.domain.integration.IntegrationUseCase
import kotlinx.coroutines.*
import javax.inject.Inject

class MobileAppIntegrationPresenterImpl @Inject constructor(
    private val view: MobileAppIntegrationView,
    private val integrationUseCase: IntegrationUseCase
) : MobileAppIntegrationPresenter {

    private val mainScope: CoroutineScope = CoroutineScope(Dispatchers.Main + Job())

    override fun onRegistrationAttempt() {

        view.showLoading()

        mainScope.launch {
            val success = integrationUseCase.registerDevice(
                BuildConfig.APPLICATION_ID,
                "Home Assistant",
                "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                "TBD",
                Build.MANUFACTURER ?: "UNKNOWN",
                Build.MODEL ?: "UNKNOWN",
                "Android",
                Build.VERSION.SDK_INT.toString(),
                false,
                null
            )
            if (success) {
                view.deviceRegistered()
            } else {
                view.showError()
            }
        }
    }

    override fun onSkip() {
        view.registrationSkipped()
    }

    override fun onFinish() {
        mainScope.cancel()
    }
}
