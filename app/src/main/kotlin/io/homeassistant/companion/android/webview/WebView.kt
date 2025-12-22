package io.homeassistant.companion.android.webview

import android.net.Uri
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

    /**
     * Loads a URL in the WebView or opens it in an external browser.
     *
     * When [openInApp] is `true`, the URL is loaded in the WebView after checking if the user
     * needs to configure security settings. If the security level hasn't been set and the user
     * hasn't previously dismissed the security prompt for this server, the
     * [io.homeassistant.companion.android.settings.ConnectionSecurityLevelFragment] is shown first.
     *
     * When [openInApp] is `false`, the URL is opened in the device's default browser.
     *
     * @param url the URL to load
     * @param keepHistory if `true`, preserves navigation history; if `false`, clears history after
     *   loading
     * @param openInApp if `true`, loads in the WebView; if `false`, opens in external browser
     * @param serverHandleInsets if `true`, the server handles window insets for edge-to-edge display
     */
    fun loadUrl(url: Uri, keepHistory: Boolean, openInApp: Boolean, serverHandleInsets: Boolean)

    fun setStatusBarAndBackgroundColor(statusBarColor: Int, backgroundColor: Int)

    fun setExternalAuth(script: String)

    fun sendExternalBusMessage(message: ExternalBusMessage)

    fun relaunchApp()

    suspend fun unlockAppIfNeeded()

    fun showError(
        errorType: ErrorType = ErrorType.TIMEOUT_GENERAL,
        error: SslError? = null,
        description: String? = null,
    )

    fun showBlockInsecure(serverId: Int)

    fun showConnectionSecurityLevel(serverId: Int)
}
