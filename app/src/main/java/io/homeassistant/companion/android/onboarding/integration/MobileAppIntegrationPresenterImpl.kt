package io.homeassistant.companion.android.onboarding.integration

import android.os.Build
import android.util.Log
import io.homeassistant.companion.android.BuildConfig
import io.homeassistant.companion.android.domain.integration.IntegrationUseCase
import kotlinx.coroutines.*
import javax.inject.Inject

class MobileAppIntegrationPresenterImpl @Inject constructor(
    private val view: MobileAppIntegrationView,
    private val integrationUseCase: IntegrationUseCase
) : MobileAppIntegrationPresenter {

    companion object {
        private const val TAG = "IntegrationPresenter"
    }

    private val mainScope: CoroutineScope = CoroutineScope(Dispatchers.Main + Job())

    override fun onRegistrationAttempt() {

        view.showLoading()

        mainScope.launch {
            try {
                integrationUseCase.registerDevice(
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
                view.deviceRegistered()
            }catch (e: Exception){
                Log.e(TAG, "Error with registering application", e)
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
