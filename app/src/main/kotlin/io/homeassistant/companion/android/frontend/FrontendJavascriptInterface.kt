package io.homeassistant.companion.android.frontend

import android.webkit.JavascriptInterface
import android.webkit.WebView
import timber.log.Timber

/**
 * Handler interface for JavaScript callbacks from the Home Assistant frontend.
 *
 * This interface is implemented by the ViewModel to handle all JavaScript-to-native
 * communication in a centralized way. The ViewModel is responsible for parsing
 * payloads and managing coroutines.
 */
interface FrontendJavascriptHandler {
    /**
     * Called when the frontend requests an authentication token.
     *
     * @param payload JSON string with callback and force parameters
     */
    fun getExternalAuth(payload: String)

    /**
     * Called when the frontend requests to revoke the authentication token.
     *
     * @param payload JSON string containing the callback function name
     */
    fun revokeExternalAuth(payload: String)

    /**
     * Called when the frontend sends an external bus message.
     *
     * @param message JSON string containing the external bus message
     */
    fun externalBus(message: String)

    /**
     * Called when the frontend theme changes (deprecated, for backwards compatibility).
     */
    fun onHomeAssistantSetTheme() {}
}

/**
 * JavaScript interface that bridges the Home Assistant frontend WebView with native Android code.
 *
 * This is a thin wrapper that adds [JavascriptInterface] annotations to the handler methods.
 * All logic (JSON parsing, coroutine launching) is handled by the [FrontendJavascriptHandler].
 *
 * @param handler Handler that receives all JavaScript callbacks (typically the ViewModel)
 */
class FrontendJavascriptInterface(private val handler: FrontendJavascriptHandler) : FrontendJavascriptHandler {

    @JavascriptInterface
    override fun getExternalAuth(payload: String) = handler.getExternalAuth(payload)

    @JavascriptInterface
    override fun revokeExternalAuth(payload: String) = handler.revokeExternalAuth(payload)

    @JavascriptInterface
    override fun externalBus(message: String) = handler.externalBus(message)

    @JavascriptInterface
    override fun onHomeAssistantSetTheme() = handler.onHomeAssistantSetTheme()

    fun attachToWebView(webView: WebView) {
        with(webView) {
            removeJavascriptInterface(INTERFACE_NAME)
            addJavascriptInterface(this@FrontendJavascriptInterface, INTERFACE_NAME)
            Timber.d("JavaScript interface attached")
        }
    }

    companion object {
        /** The name used to attach this interface to the WebView's JavaScript context. */
        private const val INTERFACE_NAME = "externalApp"

        val noOp = FrontendJavascriptInterface(
            handler = object : FrontendJavascriptHandler {
                override fun getExternalAuth(payload: String) {
                }

                override fun revokeExternalAuth(payload: String) {
                }

                override fun externalBus(message: String) {
                }
            },
        )
    }
}
