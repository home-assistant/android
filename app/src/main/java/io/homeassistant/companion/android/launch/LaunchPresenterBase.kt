package io.homeassistant.companion.android.launch

import io.homeassistant.companion.android.domain.authentication.AuthenticationUseCase
import io.homeassistant.companion.android.domain.authentication.SessionState
import io.homeassistant.companion.android.domain.integration.IntegrationUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

abstract class LaunchPresenterBase(
    private val view: LaunchView,
    private val authenticationUseCase: AuthenticationUseCase,
    internal val integrationUseCase: IntegrationUseCase
) : LaunchPresenter {

    companion object {
        const val TAG = "LaunchPresenter"
    }

    internal val mainScope: CoroutineScope = CoroutineScope(Dispatchers.Main + Job())

    override fun onViewReady() {
        mainScope.launch {
            val sessionValid = authenticationUseCase.getSessionState() == SessionState.CONNECTED
            if (sessionValid && integrationUseCase.isRegistered()) {
                resyncRegistration()
                view.displayWebview()
            } else {
                view.displayOnBoarding(sessionValid)
            }
        }
    }

    override fun setSessionExpireMillis(value: Long) {
        mainScope.launch {
            integrationUseCase.setSessionExpireMillis(value)
        }
    }

    override fun onFinish() {
        mainScope.cancel()
    }

    // TODO: This should probably go in settings?
    internal abstract fun resyncRegistration()
}
