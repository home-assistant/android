package io.homeassistant.companion.android.onboarding.authentication

import android.util.Log
import io.homeassistant.companion.android.common.data.authentication.AuthenticationRepository
import io.homeassistant.companion.android.common.data.authentication.impl.entities.LoginFlowCreateEntry
import kotlinx.coroutines.*
import javax.inject.Inject

class AuthenticationPresenterImpl @Inject constructor(
    private val view: AuthenticationView,
    private val authenticationUseCase: AuthenticationRepository
): AuthenticationPresenter{
    companion object {
        private const val TAG = "AuthenticationPresenter"
    }

    private val mainScope: CoroutineScope = CoroutineScope(Dispatchers.Main + Job())

    override fun onViewReady() {
        TODO("Not yet implemented")
    }

    override fun onNextClicked(flowId: String, username: String, password: String) {
        Log.d(TAG, "onNextClicked")
        mainScope.launch {
            val flowCreateEntry: LoginFlowCreateEntry = authenticationUseCase.loginAuthentication(flowId, username, password)
            Log.d(TAG, "Authenticated result: ${flowCreateEntry.result}")
            authenticationUseCase.registerAuthorizationCode(flowCreateEntry.result)
            Log.d(TAG, "Finished!")

            view.startIntegration()
        }
    }

    override fun onFinish() {
        mainScope.cancel()
    }
}