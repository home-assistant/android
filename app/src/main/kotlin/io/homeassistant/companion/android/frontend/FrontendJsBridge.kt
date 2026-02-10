package io.homeassistant.companion.android.frontend

import android.webkit.JavascriptInterface
import android.webkit.WebView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Handler interface for processing messages from the Home Assistant frontend.
 *
 * The Home Assistant frontend communicates with the Android app through a JavaScript bridge.
 * This interface defines the suspend functions that process those messages asynchronously.
 *
 * The frontend calls methods on the `externalApp` JavaScript interface, which are received
 * by [FrontendJsBridge] and forwarded to this handler.
 *
 * @see FrontendJsBridge
 * @see FrontendJsCallback
 */
interface FrontendJsHandler {
    /**
     * Called when the frontend requests an authentication token.
     *
     * The frontend calls this when it needs to authenticate API requests.
     * The handler should retrieve or refresh the auth token and invoke the JavaScript
     * callback specified in the payload.
     *
     * @param payload JSON string containing `callback` (JS function name) and `force` (refresh flag)
     * @param serverId The server ID to authenticate against
     */
    suspend fun getExternalAuth(payload: String, serverId: Int)

    /**
     * Called when the frontend requests to revoke the authentication session.
     *
     * This is triggered when the user logs out from the frontend.
     * The handler should clear the stored session and invoke the JavaScript callback.
     *
     * @param payload JSON string containing the `callback` function name to invoke
     * @param serverId The server ID whose session should be revoked
     */
    suspend fun revokeExternalAuth(payload: String, serverId: Int)

    /**
     * Called when the frontend sends a message through the external bus.
     *
     * The external bus is the primary communication channel between the frontend and native app.
     * Messages include commands like haptic feedback, theme changes, sensor registration, etc.
     *
     * @param message JSON string containing the bus message with `type` and optional `payload`
     */
    suspend fun externalBus(message: String)

    /**
     * Called when the frontend theme changes.
     *
     * @deprecated Kept for backwards compatibility with older frontend versions.
     * Theme changes are now communicated through [externalBus].
     */
    suspend fun onHomeAssistantSetTheme() {}
}

/**
 * Synchronous callback interface for JavaScript calls from the Home Assistant frontend.
 *
 * This interface defines the contract that JavaScript can call via `@JavascriptInterface`.
 * Methods must be synchronous (non-suspend) because JavaScript bridge calls cannot be suspended.
 *
 * Implemented by [FrontendJsBridge] which launches coroutines to bridge to the
 * suspend [FrontendJsHandler].
 *
 * @see FrontendJsBridge
 * @see FrontendJsHandler
 */
interface FrontendJsCallback {
    /**
     * Called when the frontend requests an authentication token.
     */
    fun getExternalAuth(payload: String)

    /**
     * Called when the frontend requests to revoke the authentication token.
     */
    fun revokeExternalAuth(payload: String)

    /**
     * Called when the frontend sends an external bus message.
     */
    fun externalBus(message: String)

    /**
     * Called when the frontend theme changes (deprecated, for backwards compatibility).
     */
    fun onHomeAssistantSetTheme() {}

    /**
     * Attaches this callback interface to a WebView.
     *
     * This registers the JavaScript interface so the frontend can call native methods.
     * Any previously attached interface is removed first to prevent duplicates.
     */
    fun attachToWebView(webView: WebView)
}

/**
 * JavaScript bridge that connects the Home Assistant frontend WebView with native Android code.
 *
 * This class is registered with the WebView under the name `externalApp`, allowing the
 * Home Assistant frontend to call methods like:
 * ```javascript
 * window.externalApp.getExternalAuth('{"callback":"authCallback","force":false}')
 * window.externalApp.externalBus('{"type":"config/get"}')
 * ```
 *
 * Each method is annotated with [JavascriptInterface] and launches a coroutine to call
 * the corresponding suspend method on [FrontendJsHandler].
 *
 * @param handler Handler that processes the JavaScript callbacks asynchronously
 * @param serverIdProvider Provides the current server ID for authentication operations
 * @param scope Coroutine scope for launching async operations from synchronous JS calls
 */
class FrontendJsBridge(
    private val handler: FrontendJsHandler,
    private val serverIdProvider: () -> Int,
    private val scope: CoroutineScope,
) : FrontendJsCallback {

    @JavascriptInterface
    override fun getExternalAuth(payload: String) {
        scope.launch { handler.getExternalAuth(payload, serverIdProvider()) }
    }

    @JavascriptInterface
    override fun revokeExternalAuth(payload: String) {
        scope.launch { handler.revokeExternalAuth(payload, serverIdProvider()) }
    }

    @JavascriptInterface
    override fun externalBus(message: String) {
        scope.launch { handler.externalBus(message) }
    }

    @JavascriptInterface
    override fun onHomeAssistantSetTheme() {
        scope.launch { handler.onHomeAssistantSetTheme() }
    }

    override fun attachToWebView(webView: WebView) {
        with(webView) {
            removeJavascriptInterface(INTERFACE_NAME)
            addJavascriptInterface(this@FrontendJsBridge, INTERFACE_NAME)
            Timber.d("JavaScript interface attached")
        }
    }

    companion object {
        /**
         * The name used to attach this interface to the WebView's JavaScript context.
         *
         * This name is known by the frontend. No changes can be made without proper discussion
         * and planning for backward compatibility.
         */
        private const val INTERFACE_NAME = "externalApp"

        /**
         * A no-op implementation for use in tests and previews.
         *
         * All methods are empty stubs that do nothing when called.
         */
        val noOp = object : FrontendJsCallback {
            override fun getExternalAuth(payload: String) {
            }

            override fun revokeExternalAuth(payload: String) {
            }

            override fun externalBus(message: String) {
            }

            override fun attachToWebView(webView: WebView) {
            }
        }
    }
}
