package io.homeassistant.companion.android.webview

import android.net.http.SslError
import android.webkit.HttpAuthHandler

interface WebView {

    fun loadUrl(url: String)

    fun setStatusBarColor(color: Int)

    fun setExternalAuth(script: String)

    fun openOnBoarding()

    fun showError(isAuthenticationError: Boolean = false, error: SslError? = null, description: String? = null)

    fun authenticationDialog(handler: HttpAuthHandler)
}
