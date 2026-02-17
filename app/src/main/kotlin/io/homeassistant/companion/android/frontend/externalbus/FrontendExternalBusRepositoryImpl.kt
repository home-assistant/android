package io.homeassistant.companion.android.frontend.externalbus

import io.homeassistant.companion.android.common.util.kotlinJsonMapper
import io.homeassistant.companion.android.frontend.externalbus.incoming.IncomingExternalBusMessage
import io.homeassistant.companion.android.frontend.externalbus.outgoing.OutgoingExternalBusMessage
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.plus
import io.homeassistant.companion.android.util.sensitive
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
}

/**
 * Implementation of [FrontendExternalBusRepository] that provides typed message handling
 * with polymorphic deserialization support.
 *
 * This is a self-contained implementation for the new Compose-based FrontendScreen,
 * using kotlinx.serialization for type-safe message handling.
 */
class FrontendExternalBusRepositoryImpl @Inject constructor() : FrontendExternalBusRepository {

    private val scriptsFlow = MutableSharedFlow<WebViewScript>(
        // Don't suspend if the WebView is temporarily unavailable
        extraBufferCapacity = BUFFER_CAPACITY,
    )

    private val incomingFlow = MutableSharedFlow<IncomingExternalBusMessage>(
        extraBufferCapacity = BUFFER_CAPACITY,
    )

    override suspend fun send(message: OutgoingExternalBusMessage) {
        val json = frontendExternalBusJson.encodeToString(message)
        val script = "externalBus($json);"
        Timber.d("Queuing external bus message: ${sensitive(script)}")
        scriptsFlow.emit(WebViewScript(script))
    }

    override fun scriptsToEvaluate(): Flow<WebViewScript> = scriptsFlow.asSharedFlow()

    override suspend fun evaluateScript(script: String): String? {
        val webViewScript = WebViewScript(script)
        scriptsFlow.emit(webViewScript)
        return webViewScript.result.await()
    }

    override fun incomingMessages(): Flow<IncomingExternalBusMessage> = incomingFlow.asSharedFlow()

    override suspend fun onMessageReceived(messageJson: String) {
        val message = deserializeMessage(messageJson)
        if (message != null) {
            incomingFlow.emit(message)
        }
    }

    private fun deserializeMessage(json: String): IncomingExternalBusMessage? {
        if (json.isBlank()) return null

        return runCatching {
            frontendExternalBusJson.decodeFromString<IncomingExternalBusMessage>(json)
        }.onFailure { error ->
            Timber.w(
                error,
                "Failed to deserialize external bus message: ${sensitive(json)}",
            )
        }.getOrNull()
    }
}
