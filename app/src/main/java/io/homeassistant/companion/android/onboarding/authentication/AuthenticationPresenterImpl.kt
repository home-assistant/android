package io.homeassistant.companion.android.onboarding.authentication

import android.net.Uri
import android.util.Log
import io.homeassistant.companion.android.api.AuthenticationService
import io.homeassistant.companion.android.api.HomeAssistantApi
import io.homeassistant.companion.android.api.Session
import io.homeassistant.companion.android.api.AuthorizationCode
import retrofit2.Call
import retrofit2.Response


class AuthenticationPresenterImpl(private val view: AuthenticationView) : AuthenticationPresenter {

    companion object {
        private const val TAG = "AuthenticationPresenter"
        private const val AUTH_CALLBACK = "homeassistant://auth-callback"
    }

    private lateinit var url: String

    override fun initialize(url: String) {
        this.url = url
    }

    override fun onViewReady() {
        view.loadUrl(
            Uri.parse(url)
                .buildUpon()
                .appendPath("auth")
                .appendPath("authorize")
                .appendQueryParameter("response_type", "code")
                .appendQueryParameter("client_id", AuthenticationService.CLIENT_ID)
                .appendQueryParameter("redirect_uri", AUTH_CALLBACK)
                .build()
                .toString()
        )
    }

    override fun onRedirectUrl(redirectUrl: String): Boolean {
        val callbackUrl = Uri.parse(redirectUrl)
        val code = callbackUrl.getQueryParameter("code")
        if (callbackUrl.toString().contains(AUTH_CALLBACK) && code != null) {
            HomeAssistantApi(url)
                .authenticationService
                .getToken("authorization_code", code, AuthenticationService.CLIENT_ID)
                .enqueue(object : retrofit2.Callback<AuthorizationCode> {
                    override fun onFailure(call: Call<AuthorizationCode>, t: Throwable) {
                        Log.e(TAG, "authorization code error", t)
                    }

                    override fun onResponse(call: Call<AuthorizationCode>, response: Response<AuthorizationCode>) {
                        val body = response.body()
                        if (response.isSuccessful && body != null) {
                            Session.getInstance().registerSession(body, url)
                            view.openWebview(url)
                        } else {
                            Log.e(TAG, "authorization code error ${response.errorBody()?.string()}")
                        }
                    }
                })
            return true
        } else {
            return false
        }
    }

}
