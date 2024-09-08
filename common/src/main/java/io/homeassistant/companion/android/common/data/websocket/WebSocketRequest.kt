package io.homeassistant.companion.android.common.data.websocket

import io.homeassistant.companion.android.common.data.websocket.impl.entities.SocketResponse
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharedFlow

/**
 * A class that holds information about messages that are currently active (sent and no response
 * received, or sent for a subscription) on the websocket connection.
 * @param message Map that holds the websocket message contents
 * @param timeout timeout in milliseconds for receiving a response to the message
 * @param eventFlow Flow (using callbackFlow) that will emit events for a subscription, else `null`
 * @param eventTimeout timeout in milliseconds for ending the subscription when the flow is no
 * longer collected
 * @param onEvent Channel that can receive events for a subscription, else `null`
 * @param onResponse Continuation for the initial response to this message. Don't set this when
 * creating this class, it will be set when a message is sent on the websocket.
 */
data class WebSocketRequest(
    val message: Map<*, *>,
    val timeout: Long = 30000L,
    val eventFlow: SharedFlow<Any>? = null,
    val eventTimeout: Long = 0L,
    val onEvent: Channel<Any>? = null,
    var onResponse: CancellableContinuation<SocketResponse>? = null
) {
    val hasContinuationBeenInvoked = AtomicBoolean(onResponse == null)
}
