package io.homeassistant.companion.android.onboarding.authentication

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ActivityContext
import io.homeassistant.companion.android.common.data.authentication.AuthenticationRepository
import io.homeassistant.companion.android.common.data.authentication.impl.entities.LoginFlowCreateEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

class AuthenticationPresenterImpl @Inject constructor(
    @ActivityContext context: Context,
    private val authenticationUseCase: AuthenticationRepository
) : AuthenticationPresenter {
    companion object {
        private const val TAG = "AuthenticationPresenter"
    }

    private val view = context as AuthenticationView
    private val mainScope: CoroutineScope = CoroutineScope(Dispatchers.Main + Job())

    override fun onNextClicked(flowId: String, username: String, password: String) {
        view.showLoading()
        Log.d(TAG, "onNextClicked")
        mainScope.launch {
            try {
                val flowCreateEntry: LoginFlowCreateEntry = authenticationUseCase.loginAuthentication(flowId, username, password)
                Log.d(TAG, "Authenticated result: ${flowCreateEntry.result}")
                authenticationUseCase.registerAuthorizationCode(flowCreateEntry.result)
                Log.d(TAG, "Finished!")

                view.startIntegration()
            } catch (e: Exception) {
                Log.e(TAG, "Unable to authenticate", e)
                view.showError()
            }
        }
    }

    override fun onFinish() {
        mainScope.cancel()
    }
}
