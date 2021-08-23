package io.homeassistant.companion.android.home

import android.util.Log
import io.homeassistant.companion.android.BuildConfig
import io.homeassistant.companion.android.common.data.authentication.AuthenticationRepository
import io.homeassistant.companion.android.common.data.authentication.SessionState
import io.homeassistant.companion.android.common.data.integration.DeviceRegistration
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

class HomePresenterImpl @Inject constructor(
    private val view: HomeView,
    private val authenticationUseCase: AuthenticationRepository,
    private val integrationUseCase: IntegrationRepository
) : HomePresenter {

    companion object {
        const val TAG = "HomePresenter"
    }

    private val mainScope: CoroutineScope = CoroutineScope(Dispatchers.Main + Job())

    override fun onViewReady() {
        mainScope.launch {
            val sessionValid = authenticationUseCase.getSessionState() == SessionState.CONNECTED
            if (sessionValid && integrationUseCase.isRegistered()) {
                resyncRegistration()
                // We'll stay on HomeActivity, so start loading
                view.showHomeAssistantVersion(integrationUseCase.getHomeAssistantVersion())
                view.showEntitiesCount(integrationUseCase.getEntities().size)
            } else if (sessionValid) {
                view.displayMobileAppIntegration()
            } else {
                view.displayOnBoarding()
            }
        }
    }

    override fun onLogoutClicked() {
        mainScope.launch {
            authenticationUseCase.revokeSession()
            view.displayOnBoarding()
        }
    }

    override fun onFinish() {
        mainScope.cancel()
    }

    private fun resyncRegistration() {
        mainScope.launch {
            try {
                integrationUseCase.updateRegistration(
                    DeviceRegistration(
                        "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                        null,
                        null
                    )
                )
            } catch (e: Exception) {
                Log.e(TAG, "Issue updating Registration", e)
            }
        }
    }
}
