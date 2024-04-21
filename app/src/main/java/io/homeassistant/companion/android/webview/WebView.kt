package io.homeassistant.companion.android.webview

import android.net.http.SslError
import io.homeassistant.companion.android.webview.externalbus.ExternalBusMessage

interface WebView {
    enum class ErrorType {
        AUTHENTICATION,
        SSL,
        SECURITY_WARNING,
        TIMEOUT
    }

    fun loadUrl(url: String, keepHistory: Boolean = false, openInApp: Boolean = true)

    fun setStatusBarAndNavigationBarColor(statusBarColor: Int, navigationBarColor: Int)

    fun setExternalAuth(script: String)

    fun sendExternalBusMessage(message: ExternalBusMessage)

    fun relaunchApp()

    fun unlockAppIfNeeded()

    fun showError(errorType: ErrorType = ErrorType.TIMEOUT, error: SslError? = null, description: String? = null)
}
