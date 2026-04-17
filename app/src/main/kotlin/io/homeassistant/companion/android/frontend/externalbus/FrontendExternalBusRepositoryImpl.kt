package io.homeassistant.companion.android.frontend.externalbus

import io.homeassistant.companion.android.common.util.kotlinJsonMapper
import io.homeassistant.companion.android.frontend.EvaluateScriptUsage
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
 * Implementation of [FrontendExternalBusRepository] that provides typed message handling
 * with polymorphic deserialization support.
 *
 * This is a self-contained implementation for the new Compose-based FrontendScreen,
 * using kotlinx.serialization for type-safe message handling.
 */
class FrontendExternalBusRepositoryImpl @Inject constructor() : FrontendExternalBusRepository {

    private val actionsFlow = MutableSharedFlow<WebViewAction>(
        // Don't suspend if the WebView is temporarily unavailable
        extraBufferCapacity = BUFFER_CAPACITY,
    )

    private val incomingFlow = MutableSharedFlow<IncomingExternalBusMessage>(
        extraBufferCapacity = BUFFER_CAPACITY,
    )

    /**
     * Opts into [EvaluateScriptUsage] because this is the internal mechanism that
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
    @OptIn(EvaluateScriptUsage::class)
    override suspend fun send(message: OutgoingExternalBusMessage) {
        val json = frontendExternalBusJson.encodeToString(message)
        val script = "externalBus($json);"
        Timber.d("Queuing external bus message: ${sensitive(script)}")
        actionsFlow.emit(WebViewAction.EvaluateScript(script))
    }

    override fun webViewActions(): Flow<WebViewAction> = actionsFlow.asSharedFlow()

    @EvaluateScriptUsage
    override suspend fun evaluateScript(script: String): String? {
        val action = WebViewAction.EvaluateScript(script)
        actionsFlow.emit(action)
        return action.result.await()
    }

    override fun incomingMessages(): Flow<IncomingExternalBusMessage> = incomingFlow.asSharedFlow()

    override suspend fun onMessageReceived(messageJson: JsonElement) {
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
