package io.homeassistant.companion.android.webview.externalbus

import io.homeassistant.companion.android.common.util.getStringOrNull
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.json.JsonObject
import timber.log.Timber

class ExternalBusRepositoryImpl @Inject constructor() : ExternalBusRepository {

    private val externalBusFlow = MutableSharedFlow<ExternalBusMessage>(
        // Don't suspend if the WebView is temporarily unavailable
        extraBufferCapacity = 100,
    )
    private val receiverFlows = mutableMapOf<List<String>, MutableSharedFlow<JsonObject>>()

    override suspend fun send(message: ExternalBusMessage) {
        externalBusFlow.emit(message)
    }

    override fun receive(types: List<String>): Flow<JsonObject> {
        val flow = receiverFlows[types] ?: MutableSharedFlow()
        receiverFlows[types] = flow
        return flow.asSharedFlow()
    }

    override suspend fun received(message: JsonObject) {
        val type = message.getStringOrNull("type") ?: return
        val receivers = receiverFlows.filter { it.key.contains(type) }
        Timber.d("Sending message of type $type to ${receivers.size} receiver(s)")
        receivers.forEach { (_, flow) ->
            flow.emit(message)
        }
    }

    override fun getSentFlow(): Flow<ExternalBusMessage> = externalBusFlow.asSharedFlow()
}
