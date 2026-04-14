package io.homeassistant.companion.android.frontend.js

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.annotation.VisibleForTesting
import androidx.core.net.toUri
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.util.FailFast
import io.homeassistant.companion.android.common.util.UnknownJsonContent
import io.homeassistant.companion.android.common.util.UnknownJsonContentBuilder
import io.homeassistant.companion.android.common.util.UnknownJsonContentDeserializer
import io.homeassistant.companion.android.database.server.Server
import io.homeassistant.companion.android.frontend.externalbus.frontendExternalBusJson
import io.homeassistant.companion.android.frontend.session.AuthPayload
import io.homeassistant.companion.android.util.hasSameOrigin
import io.homeassistant.companion.android.util.sensitive
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.plus
import timber.log.Timber

/**
 * Current state of the frontend.
 *
 * @property serverId The current server ID for authentication and version checks
 * @property url The current URL for V2 origin validation, or `null` if not yet resolved
 */
data class BridgeState(val serverId: Int, val url: String?)

/**
 * Parsed message received from the Home Assistant frontend via the JavaScript bridge.
 *
 * Both V1 and V2 protocols convert their raw input into a [BridgeMessage] variant
 * before dispatching to the handler via [FrontendJsBridge.dispatchToHandler].
 *
 * V2 messages are deserialized directly from the JSON envelope using [bridgeMessageJson].
 * V1 messages are constructed from the individual `@JavascriptInterface` method calls.
 */
@Serializable
@VisibleForTesting
internal sealed interface BridgeMessage {

    /** Frontend requests an authentication token. */
    @Serializable
    @SerialName("getExternalAuth")
    data class GetExternalAuth(val payload: AuthPayload) : BridgeMessage

    /** Frontend requests to revoke the authentication session. */
    @Serializable
    @SerialName("revokeExternalAuth")
    data class RevokeExternalAuth(val payload: AuthPayload) : BridgeMessage

    /** Frontend sends a message through the external bus. */
    @Serializable
    @SerialName("externalBus")
    data class ExternalBus(val payload: JsonElement) : BridgeMessage

    /** Unknown message type */
    @Serializable
    data class Unknown(override val discriminator: String?, override val content: JsonElement) :
        BridgeMessage,
        UnknownJsonContent

    companion object {
        val serializersModule = SerializersModule {
            polymorphicDefaultDeserializer(BridgeMessage::class) { className ->
                object : UnknownJsonContentDeserializer<Unknown>() {
                    override val builder = UnknownJsonContentBuilder { Unknown(className, it) }
                }
            }
        }
    }
}

/**
 * Private Json instance for deserializing V2 bridge envelopes into [BridgeMessage] variants.
 *
 * Inherits settings from [frontendExternalBusJson] (camelCase, ignoreUnknownKeys) and adds
 * the [BridgeMessage.serializersModule] for polymorphic dispatch on the `type` discriminator.
 */
private val bridgeMessageJson = Json(frontendExternalBusJson) {
    serializersModule += BridgeMessage.serializersModule
}

/**
 * JavaScript bridge that connects the Home Assistant frontend WebView with native Android code.
 *
 * This class supports two bridge protocols:
 * - **V1** (`externalApp`): Uses [WebView.addJavascriptInterface]. The frontend
 *   detects `window.externalApp` and calls named methods directly.
 * - **V2** (`externalAppV2`): Uses [WebViewCompat.addWebMessageListener]. The frontend detects
 *   `window.externalAppV2.postMessage` and sends all messages through it with a `type`
 *   discriminator. V2 provides iframe and origin filtering for improved security.
 *
 * Both protocols parse messages into [BridgeMessage] variants and route them through
 * [dispatchToHandler].
 *
 * @param handler Handler that processes the JavaScript callbacks asynchronously
 * @param serverManager Used to check server version for V2 protocol support
 * @param scope Coroutine scope for launching async operations from synchronous JS calls
 * @param stateProvider Provides the current server ID and URL from the ViewModel state
 */
class FrontendJsBridge @AssistedInject constructor(
    private val handler: FrontendJsHandler,
    private val serverManager: ServerManager,
    @Assisted private val scope: CoroutineScope,
    @Assisted private val stateProvider: () -> BridgeState,
) : FrontendJsCallback {

    /**
     * Registers the appropriate native bridge for the current server.
     *
     * Queries the server version via [serverManager] to determine whether to use the
     * V2 bridge or the legacy V1 bridge. V2 also requires
     * [WebViewFeature.WEB_MESSAGE_LISTENER] support; falls back to V1 if unavailable.
     *
     * Safe to call multiple times: each path removes the previously registered interface
     * before adding the new one.
     */
    @SuppressLint("RequiresFeature")
    override suspend fun attachToWebView(webView: WebView) {
        val useV2 = serverManager.getServer(stateProvider().serverId).isServerSupportingExternalAppV2()
        val webMessageListenerSupported = WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)
        if (useV2 && webMessageListenerSupported) {
            webView.removeJavascriptInterface(EXTERNAL_APP_V1)
            registerV2(webView)
        } else {
            if (webMessageListenerSupported) {
                WebViewCompat.removeWebMessageListener(webView, EXTERNAL_APP_V2_LISTENER)
            }
            registerV1(webView)
        }
    }

    /**
     * Dispatches a parsed [BridgeMessage] to the [handler].
     *
     * Validates auth callback names at this layer before forwarding to the handler.
     * This is the shared routing logic used by both V1 and V2 protocols.
     */
    private fun dispatchToHandler(message: BridgeMessage) {
        scope.launch {
            when (message) {
                is BridgeMessage.GetExternalAuth -> {
                    if (FailFast.failWhen(message.payload.callback != EXPECTED_GET_AUTH_CALLBACK) {
                            "Aborting getExternalAuth: callback '${message.payload.callback}' does not match expected '$EXPECTED_GET_AUTH_CALLBACK'"
                        }
                    ) {
                        return@launch
                    }
                    handler.getExternalAuth(message.payload, stateProvider().serverId)
                }

                is BridgeMessage.RevokeExternalAuth -> {
                    if (FailFast.failWhen(message.payload.callback != EXPECTED_REVOKE_AUTH_CALLBACK) {
                            "Aborting revokeExternalAuth: callback '${message.payload.callback}' does not match expected '$EXPECTED_REVOKE_AUTH_CALLBACK'"
                        }
                    ) {
                        return@launch
                    }
                    handler.revokeExternalAuth(message.payload, stateProvider().serverId)
                }

                is BridgeMessage.ExternalBus -> handler.externalBus(message.payload)

                is BridgeMessage.Unknown -> FailFast.fail {
                    "Unknown bridge message type '${message.discriminator}' content ${
                        sensitive {
                            message.content.toString()
                        }
                    }"
                }
            }
        }
    }

    /**
     * Registers the legacy V1 bridge using [WebView.addJavascriptInterface].
     *
     * The HA frontend detects `window.externalApp` and calls named methods directly.
     * Each method parses its raw JSON string into the corresponding [BridgeMessage] variant.
     */
    private fun registerV1(webView: WebView) {
        webView.removeJavascriptInterface(EXTERNAL_APP_V1)
        webView.addJavascriptInterface(
            object : Any() {
                @JavascriptInterface
                fun getExternalAuth(payload: String) {
                    val authPayload = parseAuthPayload(payload) ?: return
                    dispatchToHandler(BridgeMessage.GetExternalAuth(payload = authPayload))
                }

                @JavascriptInterface
                fun revokeExternalAuth(payload: String) {
                    val authPayload = parseAuthPayload(payload) ?: return
                    dispatchToHandler(BridgeMessage.RevokeExternalAuth(payload = authPayload))
                }

                @JavascriptInterface
                fun externalBus(message: String) {
                    val json = parseJsonElement(message) ?: return
                    dispatchToHandler(BridgeMessage.ExternalBus(payload = json))
                }
            },
            EXTERNAL_APP_V1,
        )
        Timber.d("JS $EXTERNAL_APP_V1 interface added")
    }

    /**
     * Registers the V2 bridge using [WebViewCompat.addWebMessageListener].
     *
     * The HA frontend detects `window.externalAppV2.postMessage` and sends all messages
     * through it with a `type` discriminator. Messages are rejected if they come from
     * an iframe or from an origin that doesn't match the currently loaded server URL.
     *
     * The raw JSON envelope is deserialized directly into a [BridgeMessage] variant.
     */
    @SuppressLint("RequiresFeature")
    private fun registerV2(webView: WebView) {
        WebViewCompat.removeWebMessageListener(webView, EXTERNAL_APP_V2_LISTENER)
        WebViewCompat.addWebMessageListener(
            webView,
            EXTERNAL_APP_V2_LISTENER,
            setOf("*"),
        ) { _, message, sourceOrigin, isMainFrame, _ ->
            if (!isMainFrame) {
                Timber.w("Ignored message from iframe")
                return@addWebMessageListener
            }
            val currentUri = stateProvider().url?.toUri()
            if (!sourceOrigin.hasSameOrigin(currentUri)) {
                Timber.w(
                    "Ignored message from unexpected origin: ${sensitive(sourceOrigin.toString())} current ${
                        sensitive {
                            currentUri.toString()
                        }
                    }",
                )
                return@addWebMessageListener
            }

            val data = message.data ?: return@addWebMessageListener
            val bridgeMessage = parseBridgeMessage(data) ?: return@addWebMessageListener
            dispatchToHandler(bridgeMessage)
        }
        Timber.d("JS $EXTERNAL_APP_V2_LISTENER listener added")
    }

    private fun parseAuthPayload(json: String): AuthPayload? {
        return FailFast.failOnCatch(
            message = { "Failed to parse auth payload" },
            fallback = null,
        ) { frontendExternalBusJson.decodeFromString<AuthPayload>(json) }
    }

    private fun parseJsonElement(json: String): JsonElement? {
        return FailFast.failOnCatch(
            message = { "Failed to parse JSON element" },
            fallback = null,
        ) { frontendExternalBusJson.parseToJsonElement(json) }
    }

    private fun parseBridgeMessage(json: String): BridgeMessage? {
        return FailFast.failOnCatch(
            message = { "Failed to parse V2 bridge message" },
            fallback = null,
        ) { bridgeMessageJson.decodeFromString<BridgeMessage>(json) }
    }

    companion object {
        /**
         * The only callback name the app accepts for `getExternalAuth` requests.
         *
         * Requests with a different callback name are rejected.
         */
        const val EXPECTED_GET_AUTH_CALLBACK = "externalAuthSetToken"

        /**
         * The only callback name the app accepts for `revokeExternalAuth` requests.
         *
         * Requests with a different callback name are rejected.
         */
        const val EXPECTED_REVOKE_AUTH_CALLBACK = "externalAuthRevokeToken"

        /**
         * The JavaScript interface name for the V1 bridge.
         *
         * The frontend detects `window.externalApp` and calls methods on it directly.
         * This name is part of the frontend contract do not change without coordinating
         * with the Home Assistant frontend team.
         */
        const val EXTERNAL_APP_V1 = "externalApp"

        /**
         * The listener name for the V2 bridge.
         *
         * The frontend detects `window.externalAppV2.postMessage` and sends
         * JSON-encoded messages through it. This name is part of the frontend contract
         * do not change without coordinating with the Home Assistant frontend team.
         */
        const val EXTERNAL_APP_V2_LISTENER = "externalAppV2"

        /**
         * Returns a JS `function()` expression that sends [jsonPayload] through the external bus.
         *
         * The returned string is a complete `function() { ... }` expression that can be used
         * directly as a callback.
         *
         * For V1: calls `window.externalApp.externalBus(...)` directly.
         * For V2: posts a `{type:'externalBus', payload:...}` message via `window.externalAppV2`.
         */
        fun externalBusCallback(jsonPayload: String): String = """
        function() {
            if (typeof window.$EXTERNAL_APP_V2_LISTENER !== 'undefined') {
                window.$EXTERNAL_APP_V2_LISTENER.postMessage(JSON.stringify({type:'externalBus',payload:$jsonPayload}));
            } else {
                window.$EXTERNAL_APP_V1.externalBus(JSON.stringify($jsonPayload));
            }
        }
        """.trimIndent()

        /**
         * Whether this server supports the V2 bridge protocol.
         *
         * V2 was introduced in Home Assistant 2026.4.2.
         */
        fun Server?.isServerSupportingExternalAppV2(): Boolean = this?.version?.isAtLeast(2026, 4, 2) == true

        /** A no-op implementation for use in tests and previews. */
        val noOp = object : FrontendJsCallback {
            override suspend fun attachToWebView(webView: WebView) {}
        }
    }
}

/**
 * Factory for creating [FrontendJsBridge] instances via assisted injection.
 */
@AssistedFactory
interface FrontendJsBridgeFactory {
    /**
     * Creates a new [FrontendJsBridge].
     *
     * @param scope Coroutine scope for launching async operations from synchronous JS calls
     * @param stateProvider Provides the current server ID and URL from the ViewModel state
     */
    fun create(scope: CoroutineScope, stateProvider: () -> BridgeState): FrontendJsBridge
}
