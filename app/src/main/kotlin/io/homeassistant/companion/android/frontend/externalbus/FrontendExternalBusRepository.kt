package io.homeassistant.companion.android.frontend.externalbus

import io.homeassistant.companion.android.frontend.externalbus.incoming.IncomingExternalBusMessage
import io.homeassistant.companion.android.frontend.externalbus.outgoing.OutgoingExternalBusMessage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow

/**
 * Represents a JavaScript script to be evaluated in the WebView.
 *
 * @property script The JavaScript code to evaluate
 * @property result Deferred that will be completed with the evaluation result.
 *                  The WebView consumer should call [result.complete] with the result.
 */
data class WebViewScript(val script: String, val result: CompletableDeferred<String?> = CompletableDeferred())

/**
 * Repository for typed communication with the Home Assistant frontend via the external bus.
 *
 * This repository provides type-safe message handling for the new Compose-based FrontendScreen.
 * Messages are serialized/deserialized using kotlinx.serialization with polymorphic support,
 * allowing graceful handling of unknown message types from newer Home Assistant versions.
 *
 * @see <a href="https://developers.home-assistant.io/docs/frontend/external-bus">External bus documentation</a>
 */
interface FrontendExternalBusRepository {

    /**
     * Sends a typed message to the frontend via the external bus.
     */
    suspend fun send(message: OutgoingExternalBusMessage)

    /**
     * Returns a flow of scripts to evaluate in the WebView.
     *
     * The WebView should collect this flow and call `evaluateJavascript` for each script.
     */
    fun scriptsToEvaluate(): Flow<WebViewScript>

    /**
     * Evaluates a raw JavaScript script in the WebView and returns the result.
     *
     * This suspends until the WebView evaluates the script and returns the result.
     *
     * @return The evaluation result from the WebView, or null if the script returns no value
     */
    suspend fun evaluateScript(script: String): String?

    /**
     * Returns a flow of typed incoming messages from the frontend.
     *
     * Unknown message types are emitted as [io.homeassistant.companion.android.frontend.externalbus.incoming.UnknownIncomingMessage]
     * instead of throwing exceptions, ensuring forward compatibility with newer frontend versions.
     */
    fun incomingMessages(): Flow<IncomingExternalBusMessage>

    /**
     * Called by the WebView JavaScript interface when a message is received from the frontend.
     *
     * The message is deserialized and emitted to subscribers of [incomingMessages].
     *
     * @param messageJson The raw JSON string from the frontend
     */
    suspend fun onMessageReceived(messageJson: String)
}
