package io.homeassistant.companion.android.webview

import android.net.http.SslError

interface WebView {
    enum class ErrorType {
        AUTHENTICATION,
        SSL,
        SECURITY_WARNING,
        TIMEOUT
    }

    fun loadUrl(url: String, keepHistory: Boolean, openInApp: Boolean)

    fun setStatusBarAndNavigationBarColor(statusBarColor: Int, navigationBarColor: Int)

    fun setExternalAuth(script: String)

    fun relaunchApp()

    fun unlockAppIfNeeded()

    fun showError(errorType: ErrorType = ErrorType.TIMEOUT, error: SslError? = null, description: String? = null)
}
