package io.homeassistant.companion.android.onboarding.authentication

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ActivityContext
import io.homeassistant.companion.android.common.data.authentication.AuthenticationRepository
import io.homeassistant.companion.android.common.data.authentication.impl.entities.LoginFlowCreateEntry
import io.homeassistant.companion.android.common.data.authentication.impl.entities.LoginFlowForm
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

    enum class AuthenticationPhase {
        PASSWORD, MFA
    }
    private var authenticationPhase = AuthenticationPhase.PASSWORD

    private val view = context as AuthenticationView
    private val mainScope: CoroutineScope = CoroutineScope(Dispatchers.Main + Job())

    override fun onNextClicked(flowId: String, username: String?, password: String?, code: String?) {
        view.showLoading()
        Log.d(TAG, "onNextClicked")
        mainScope.launch {
            try {
                val flowResult = when (authenticationPhase) {
                    AuthenticationPhase.PASSWORD -> authenticationUseCase.loginAuthentication(flowId, username.orEmpty(), password.orEmpty())
                    AuthenticationPhase.MFA -> authenticationUseCase.loginCode(flowId, code.orEmpty())
                }
                Log.d(TAG, "Authenticated result type: ${flowResult?.type}")
                when (flowResult?.type) {
                    "form" -> {
                        if (authenticationPhase == AuthenticationPhase.PASSWORD && (flowResult as LoginFlowForm).stepId == "mfa") {
                            Log.d(TAG, "MFA required to authenticate")
                            authenticationPhase = AuthenticationPhase.MFA
                            view.showMfa()
                        } else {
                            Log.e(TAG, "Unable to authenticate")
                            view.showError()
                        }
                    }
                    "create_entry" -> {
                        authenticationUseCase.registerAuthorizationCode((flowResult as LoginFlowCreateEntry).result)
                        Log.d(TAG, "Finished!")

                        view.startIntegration()
                    }
                    else -> {
                        Log.e(TAG, "Unable to authenticate")
                        view.showError()
                    }
                }
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
