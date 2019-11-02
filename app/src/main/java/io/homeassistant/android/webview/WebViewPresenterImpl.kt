package io.homeassistant.android.webview

import android.util.Log
import io.homeassistant.android.api.AuthenticationService
import io.homeassistant.android.api.HomeAssistantApi
import io.homeassistant.android.api.RefreshToken
import io.homeassistant.android.api.Session
import retrofit2.Response
import java.io.IOException


class WebViewPresenterImpl(private val view: WebView) : WebViewPresenter {

    companion object {
        private const val TAG = "WebViewPresenterImpl"
    }

    private lateinit var url: String

    override fun onViewReady() {
        url = Session.getInstance().url ?: throw IllegalStateException("Unable to display the webview if we do not have url")
        view.loadUrl(url)
    }


    override fun onGetExternalAuth(callback: String) {
        val token = Session.getInstance().token ?: throw IllegalStateException("Unable to display the webview if we do not have token")
        if (token.isExpired()) {
            val response: Response<RefreshToken>
            try {
                response = HomeAssistantApi(url)
                    .authenticationService
                    .refreshToken("refresh_token", token.refreshToken, AuthenticationService.CLIENT_ID)
                    .execute()
            } catch (e: IOException) {
                Log.e(TAG, "refresh token error", e)
                return
            } catch (e: RuntimeException) {
                Log.e(TAG, "refresh token error", e)
                return
            }

            val body = response.body()
            if (response.isSuccessful && body != null) {
                Session.getInstance().registerRefreshToken(body, url)
                view.setToken(callback, Session.getInstance().token!!)
            } else {
                Log.e(TAG, "refresh token error ${response.errorBody()?.string()}")
            }
        } else {
            view.setToken(callback, token)
        }
    }

}