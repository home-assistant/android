package io.homeassistant.companion.android.webview

import android.webkit.HttpAuthHandler

interface WebView {

    fun loadUrl(url: String)

    fun setStatusBarColor(color: Int)

    fun setExternalAuth(script: String)

    fun openOnBoarding()

    fun showError(isAuthenticationError: Boolean = false)

    fun authenticationDialog(handler: HttpAuthHandler)
}
