package io.homeassistant.companion.android.launch

import io.homeassistant.companion.android.domain.authentication.AuthenticationUseCase
import io.homeassistant.companion.android.domain.authentication.SessionState
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class LaunchPresenterImpl @Inject constructor(
    private val view: LaunchView,
    private val authenticationUseCase: AuthenticationUseCase
) : LaunchPresenter {

    private val mainScope: CoroutineScope = CoroutineScope(Dispatchers.Main + Job())

    override fun onViewReady() {
        mainScope.launch {
            if (authenticationUseCase.getSessionState() == SessionState.CONNECTED) {
                view.displayWebview()
            } else {
                view.displayOnBoarding()
            }
        }
    }

    override fun onFinish() {
        mainScope.cancel()
    }
}
