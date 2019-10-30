package io.homeassistant.android.webview

import android.util.Log
import io.homeassistant.android.api.AuthenticationService
import io.homeassistant.android.api.HomeAssistantApi
import io.homeassistant.android.api.RefreshToken
import io.homeassistant.android.api.Session
import io.homeassistant.android.io.homeassistant.android.api.Token
import retrofit2.Call
import retrofit2.Response


class WebViewPresenterImpl(private val view: WebView) : WebViewPresenter {

    companion object {
        private const val TAG = "WebViewPresenterImpl"
    }

    override fun onViewReady() {
        val token = Session.getInstance().token ?: throw IllegalStateException("Unable to display the webview if we do not have token")
        val url = Session.getInstance().url ?: throw IllegalStateException("Unable to display the webview if we do not have url")
        if (token.isExpired()) {
            HomeAssistantApi(url)
                .authenticationService
                .refreshToken("refresh_token", token.refreshToken, AuthenticationService.CLIENT_ID)
                .enqueue(object : retrofit2.Callback<RefreshToken> {
                    override fun onFailure(call: Call<RefreshToken>, t: Throwable) {
                        Log.e(TAG, "refresh token", t)
                    }

                    override fun onResponse(call: Call<RefreshToken>, response: Response<RefreshToken>) {
                        val body = response.body()
                        if (response.isSuccessful && body != null) {
                            Session.getInstance().registerRefreshToken(body, url)
                            loadUrl(Session.getInstance().token!!, url)
                        } else {
                            Log.e(TAG, "refresh token error ${response.errorBody()?.string()}")
                        }
                    }
                })
        } else {
            loadUrl(token, url)
        }
    }

    private fun loadUrl(token: Token, url: String) {
        view.setupJavascriptInterface(token)
        view.loadUrl(url)
    }
}