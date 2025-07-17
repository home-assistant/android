package io.homeassistant.companion.android.common.data.websocket

import io.homeassistant.companion.android.common.data.websocket.impl.entities.RawMessageSocketResponse
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharedFlow

/**
 * A class that holds information about messages that are currently active (sent and no response
 * received, or sent for a subscription) on the websocket connection.
 * @param message Map that holds the websocket message contents
 * @param timeout The maximum duration to wait for a response to the request. If no
 *                  response is received within this duration, the request will timeout.
 *                  Defaults to 30 seconds.
 * @param eventFlow Flow (using callbackFlow) that will emit events for a subscription, else `null`
 * @param onEvent Channel that can receive events for a subscription, else `null`
 */
internal data class WebSocketRequest(
    val message: Map<String, Any?>,
    val timeout: Duration = 30.seconds,
    val eventFlow: SharedFlow<Any>? = null,
    val onEvent: Channel<Any>? = null,
) {
    // These variables are set when a message is sent on the websocket.
    var onResponse: CancellableContinuation<RawMessageSocketResponse>? = null
    val hasContinuationBeenInvoked = AtomicBoolean(false)
}
