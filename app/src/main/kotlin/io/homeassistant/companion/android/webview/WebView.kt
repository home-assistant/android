package io.homeassistant.companion.android.webview

import android.net.http.SslError
import io.homeassistant.companion.android.webview.externalbus.ExternalBusMessage

interface WebView {
    enum class ErrorType {
        AUTHENTICATION,
        SSL,
        SECURITY_WARNING,

        /** Timeout or general loading error */
        TIMEOUT_GENERAL,

        /** Timeout due to no 'connection-status: connected' event on the external bus */
        TIMEOUT_EXTERNAL_BUS,
    }

    fun loadUrl(url: String, keepHistory: Boolean, openInApp: Boolean)

    fun setStatusBarAndNavigationBarColor(statusBarColor: Int, navigationBarColor: Int)

    fun setExternalAuth(script: String)

    fun sendExternalBusMessage(message: ExternalBusMessage)

    fun relaunchApp()

    fun unlockAppIfNeeded()

    fun showError(errorType: ErrorType = ErrorType.TIMEOUT_GENERAL, error: SslError? = null, description: String? = null)
}
