package io.homeassistant.companion.android.frontend.externalbus

import dagger.hilt.android.scopes.ViewModelScoped
import io.homeassistant.companion.android.common.util.kotlinJsonMapper
import io.homeassistant.companion.android.frontend.EvaluateJavascriptUsage
import io.homeassistant.companion.android.frontend.WebViewAction
import io.homeassistant.companion.android.frontend.externalbus.incoming.HapticType
import io.homeassistant.companion.android.frontend.externalbus.incoming.IncomingExternalBusMessage
import io.homeassistant.companion.android.frontend.externalbus.outgoing.OutgoingExternalBusMessage
import io.homeassistant.companion.android.util.sensitive
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.modules.plus
import timber.log.Timber

private const val BUFFER_CAPACITY = 10

/**
 * JSON serializer configured for Home Assistant frontend communication.
 *
 * The frontend uses camelCase naming (e.g., `externalBus`, `authCallback`), unlike the
 * Home Assistant core API which uses snake_case. Setting `namingStrategy = null` disables
 * the default snake_case conversion from [kotlinJsonMapper].
 */
@OptIn(ExperimentalSerializationApi::class)
val frontendExternalBusJson = Json(kotlinJsonMapper) {
    namingStrategy = null
    serializersModule += IncomingExternalBusMessage.serializersModule
    serializersModule += HapticType.serializersModule
}

/**
 * Repository for typed communication with the Home Assistant frontend via the external bus.
 *
 * This repository provides type-safe message handling for the FrontendScreen.
 * Messages are serialized/deserialized using kotlinx.serialization with polymorphic support,
 * allowing graceful handling of unknown message types from newer Home Assistant versions.
 *
 * @see <a href="https://developers.home-assistant.io/docs/frontend/external-bus">External bus documentation</a>
 */
@ViewModelScoped
class FrontendExternalBusRepository @Inject constructor() {

    private val actionsFlow = MutableSharedFlow<WebViewAction>(
        // Don't suspend if the WebView is temporarily unavailable
        extraBufferCapacity = BUFFER_CAPACITY,
    )

    private val incomingFlow = MutableSharedFlow<IncomingExternalBusMessage>(
        extraBufferCapacity = BUFFER_CAPACITY,
    )

    /**
     * Sends a typed message to the frontend via the external bus.
     *
     * The message is serialized to JSON and emitted as a [WebViewAction.EvaluateScript].
     * This is fire-and-forget — the evaluation result is not awaited.
     */
    /*
     * Opts into [EvaluateJavascriptUsage] because this is the internal mechanism that
     * implements the external bus itself. The frontend installs `window.externalBus`
     * as the entry point for messages from the native app (see `ExternalMessaging.attach`
     * in the frontend), so delivering a bus message means invoking that function via
     * `evaluateJavascript`. There is no higher-level alternative — this call is what
     * other callers rely on when they use [send].
     *
     * [send] enforces strong typing by accepting an [OutgoingExternalBusMessage] parameter,
     * hiding the JSON serialization and script wrapping from callers. This keeps the raw
     * `evaluateScript` usage confined to this single site so callers do not have to construct
     * arbitrary scripts and use the typed message API.
     */
    @OptIn(EvaluateJavascriptUsage::class)
    suspend fun send(message: OutgoingExternalBusMessage) {
        val json = frontendExternalBusJson.encodeToString(message)
        val script = "externalBus($json);"
        Timber.d("Queuing external bus message: ${sensitive(script)}")
        actionsFlow.emit(WebViewAction.EvaluateScript(script))
    }

    /**
     * Returns a flow of [WebViewAction] to be executed by the WebView.
     *
     * The WebView should collect this flow and execute each action accordingly.
     */
    fun webViewActions(): Flow<WebViewAction> = actionsFlow.asSharedFlow()

    /**
     * Evaluates a raw JavaScript script in the WebView and returns the result.
     *
     * This suspends until the WebView evaluates the script and returns the result.
     *
     * @return The evaluation result from the WebView, or null if the script returns no value
     */
    @EvaluateJavascriptUsage
    suspend fun evaluateScript(script: String): String? {
        val action = WebViewAction.EvaluateScript(script)
        actionsFlow.emit(action)
        return action.result.await()
    }

    /**
     * Returns a flow of typed incoming messages from the frontend.
     *
     * Unknown message types are emitted as [io.homeassistant.companion.android.frontend.externalbus.incoming.UnknownIncomingMessage]
     * instead of throwing exceptions, ensuring forward compatibility with newer frontend versions.
     */
    fun incomingMessages(): Flow<IncomingExternalBusMessage> = incomingFlow.asSharedFlow()

    /**
     * Called by the WebView JavaScript interface when a message is received from the frontend.
     *
     * The message is deserialized and emitted to subscribers of [incomingMessages].
     *
     * @param messageJson The JSON message from the frontend
     */
    suspend fun onMessageReceived(messageJson: JsonElement) {
        val message = deserializeMessage(messageJson)
        if (message != null) {
            incomingFlow.emit(message)
        }
    }

    private fun deserializeMessage(json: JsonElement): IncomingExternalBusMessage? {
        return runCatching {
            frontendExternalBusJson.decodeFromJsonElement<IncomingExternalBusMessage>(json)
        }.onFailure { error ->
            Timber.w(
                error,
                "Failed to deserialize external bus message: ${sensitive { json.toString() }}",
            )
        }.getOrNull()
    }
}
