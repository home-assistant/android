package io.shpro.companion.android.webview.externalbus

import android.util.Log
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.json.JSONObject

class ExternalBusRepositoryImpl @Inject constructor() : ExternalBusRepository {

    companion object {
        private const val TAG = "ExternalBusRepo"
    }

    private val externalBusFlow = MutableSharedFlow<ExternalBusMessage>(
        // Don't suspend if the WebView is temporarily unavailable
        extraBufferCapacity = 100
    )
    private val receiverFlows = mutableMapOf<List<String>, MutableSharedFlow<JSONObject>>()

    override suspend fun send(message: ExternalBusMessage) {
        externalBusFlow.emit(message)
    }

    override fun receive(types: List<String>): Flow<JSONObject> {
        val flow = receiverFlows[types] ?: MutableSharedFlow()
        receiverFlows[types] = flow
        return flow.asSharedFlow()
    }

    override suspend fun received(message: JSONObject) {
        if (!message.has("type")) return
        val type = message.getString("type")
        val receivers = receiverFlows.filter { it.key.contains(type) }
        Log.d(TAG, "Sending message of type $type to ${receivers.size} receiver(s)")
        receivers.forEach { (_, flow) ->
            flow.emit(message)
        }
    }

    override fun getSentFlow(): Flow<ExternalBusMessage> = externalBusFlow.asSharedFlow()
}
