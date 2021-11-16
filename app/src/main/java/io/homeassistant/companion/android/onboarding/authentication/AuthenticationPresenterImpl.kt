package io.homeassistant.companion.android.onboarding.authentication

import android.net.Uri
import android.util.Log
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.data.authentication.AuthenticationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

class AuthenticationPresenterImpl @Inject constructor(
    private val authenticationUseCase: AuthenticationRepository
) : AuthenticationPresenter {

    companion object {
        private const val TAG = "AuthenticationPresenter"
        private const val AUTH_CALLBACK = "homeassistant://auth-callback"
    }

    private val mainScope: CoroutineScope = CoroutineScope(Dispatchers.Main + Job())

    private lateinit var view: AuthenticationView

    // TODO: Fix me by moving to ViewModels!
    override fun init(authenticationView: AuthenticationView) {
        view = authenticationView
    }

    override fun onViewReady() {
        mainScope.launch {
            try {
                view.loadUrl(authenticationUseCase.buildAuthenticationUrl(AUTH_CALLBACK).toString())
            } catch (e: Exception) {
                Log.e(TAG, "Unable to create auth url and/or load it.", e)
                view.showError(R.string.webview_error, null, null)
            }
        }
    }

    override fun onRedirectUrl(redirectUrl: String): Boolean {
        val code = Uri.parse(redirectUrl).getQueryParameter("code")
        return if (redirectUrl.startsWith(AUTH_CALLBACK) && !code.isNullOrBlank()) {
            mainScope.launch {
                try {
                    authenticationUseCase.registerAuthorizationCode(code)
                } catch (e: Exception) {
                    Log.e(TAG, "unable to register code", e)
                    view.showError(R.string.webview_error, null, null)
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
