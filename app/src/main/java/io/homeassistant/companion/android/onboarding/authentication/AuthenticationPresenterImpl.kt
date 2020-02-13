package io.homeassistant.companion.android.onboarding.authentication

import android.net.Uri
import android.util.Log
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.domain.authentication.AuthenticationUseCase
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class AuthenticationPresenterImpl @Inject constructor(
    private val view: AuthenticationView,
    private val authenticationUseCase: AuthenticationUseCase
) : AuthenticationPresenter {

    companion object {
        private const val TAG = "AuthenticationPresenter"
        private const val AUTH_CALLBACK = "homeassistant://auth-callback"
    }

    private val mainScope: CoroutineScope = CoroutineScope(Dispatchers.Main + Job())

    override fun onViewReady() {
        mainScope.launch {
            try {
                view.loadUrl(authenticationUseCase.buildAuthenticationUrl(AUTH_CALLBACK).toString())
            } catch (e: Exception) {
                Log.e(TAG, "Unable to create auth url and/or load it.", e)
                view.showError(R.string.webview_error)
            }
        }
    }

    override fun onRedirectUrl(redirectUrl: String): Boolean {
        val code = Uri.parse(redirectUrl).getQueryParameter("code")
        return if (redirectUrl.contains(AUTH_CALLBACK) && !code.isNullOrBlank()) {
            mainScope.launch {
                try {
                    authenticationUseCase.registerAuthorizationCode(code)
                } catch (e: Exception) {
                    Log.e(TAG, "unable to register code")
                    view.showError(R.string.webview_error)
                    return@launch
                }
                view.openWebview()
            }
            true
        } else {
            false
        }
    }

    override fun onFinish() {
        mainScope.cancel()
    }
}
