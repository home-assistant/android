package io.homeassistant.companion.android.frontend.js

import android.webkit.WebView

/**
 * Interface for registering the frontend JavaScript callbacks into a WebView.
 *
 * Implementations are responsible for setting up the communication channel between
 * the Home Assistant frontend and native Android code so that the frontend can invoke
 * native callbacks (authentication, external bus, etc.).
 *
 * @see FrontendJsBridge
 */
interface FrontendJsCallback {
    /**
     * Registers the JavaScript callbacks into the given [webView].
     *
     * Must be called before loading a URL so the frontend can discover the bridge.
     *
     * @param webView The WebView to register callbacks into
     */
    suspend fun attachToWebView(webView: WebView)
}
