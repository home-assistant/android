package io.homeassistant.companion.android.common.frontend.externalbus

import io.homeassistant.companion.android.common.BuildConfig
import io.homeassistant.companion.android.common.frontend.externalbus.incoming.IncomingExternalBusMessage
import io.homeassistant.companion.android.common.frontend.externalbus.outgoing.OutgoingExternalBusMessage
import io.homeassistant.companion.android.common.util.kotlinJsonMapper
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import timber.log.Timber

/**
 * Implementation of [FrontendExternalBusRepository] that provides typed message handling
 * with polymorphic deserialization support.
 *
 * This is a self-contained implementation for the new Compose-based FrontendScreen,
 * using kotlinx.serialization for type-safe message handling.
 */
class FrontendExternalBusRepositoryImpl @Inject constructor() : FrontendExternalBusRepository {

    // TODO do we really need a buffer or can make it a cold flow
    private val scriptsFlow = MutableSharedFlow<WebViewScript>(
        // Don't suspend if the WebView is temporarily unavailable
        extraBufferCapacity = 100,
    )

    private val incomingFlow = MutableSharedFlow<IncomingExternalBusMessage>(
        extraBufferCapacity = 100,
    )

    override suspend fun send(message: OutgoingExternalBusMessage) {
        val json = kotlinJsonMapper.encodeToString(message)
        val script = "externalBus($json);"
        Timber.d("Queuing external bus message: $script")
        scriptsFlow.emit(WebViewScript(script))
    }

    override fun scriptsToEvaluate(): Flow<WebViewScript> = scriptsFlow.asSharedFlow()

    override suspend fun evaluateScript(script: String): String? {
        Timber.d("Queuing script: $script")
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
            kotlinJsonMapper.decodeFromString<IncomingExternalBusMessage>(json)
        }.onFailure { error ->
            Timber.w(
                error,
                "Failed to deserialize external bus message: ${if (BuildConfig.DEBUG) json else "HIDDEN"}",
            )
        }.getOrNull()
    }
}
