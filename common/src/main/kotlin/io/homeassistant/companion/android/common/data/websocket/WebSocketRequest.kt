package io.homeassistant.companion.android.common.data.websocket

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Represents a request to be sent over the WebSocket connection.
 *
 * @param message Map that holds the websocket message contents (excluding the ID, which is added internally)
 * @param timeout The maximum duration to wait for a response. Defaults to 30 seconds.
 */
internal data class WebSocketRequest(val message: Map<String, Any?>, val timeout: Duration = 30.seconds)
