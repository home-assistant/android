package io.homeassistant.companion.android.frontend.externalbus

import io.homeassistant.companion.android.frontend.EvaluateScriptUsage
import io.homeassistant.companion.android.frontend.WebViewAction
import io.homeassistant.companion.android.frontend.externalbus.incoming.IncomingExternalBusMessage
import io.homeassistant.companion.android.frontend.externalbus.outgoing.OutgoingExternalBusMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonElement

/**
 * Repository for typed communication with the Home Assistant frontend via the external bus.
 *
 * This repository provides type-safe message handling for the FrontendScreen.
 * Messages are serialized/deserialized using kotlinx.serialization with polymorphic support,
 * allowing graceful handling of unknown message types from newer Home Assistant versions.
 *
 * @see <a href="https://developers.home-assistant.io/docs/frontend/external-bus">External bus documentation</a>
 */
interface FrontendExternalBusRepository {

    /**
     * Sends a typed message to the frontend via the external bus.
     *
     * The message is serialized to JSON and emitted as a [WebViewAction.EvaluateScript].
     * This is fire-and-forget — the evaluation result is not awaited.
     */
    suspend fun send(message: OutgoingExternalBusMessage)

    /**
     * Returns a flow of [WebViewAction] to be executed by the WebView.
     *
     * The WebView should collect this flow and execute each action accordingly.
     */
    fun webViewActions(): Flow<WebViewAction>

    /**
     * Evaluates a raw JavaScript script in the WebView and returns the result.
     *
     * This suspends until the WebView evaluates the script and returns the result.
     *
     * @return The evaluation result from the WebView, or null if the script returns no value
     */
    @EvaluateScriptUsage
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
     * @param messageJson The JSON message from the frontend
     */
    suspend fun onMessageReceived(messageJson: JsonElement)
}
