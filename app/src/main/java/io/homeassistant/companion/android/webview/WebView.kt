package io.homeassistant.companion.android.webview

interface WebView {

    fun loadUrl(url: String)

    fun setStatusBarColor(color: Int)

    fun setExternalAuth(script: String)

    fun openOnBoarding()

    fun showError(isAuthenticationError: Boolean = false)
}
