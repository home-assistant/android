package io.homeassistant.companion.android.common.data.websocket

import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.data.websocket.impl.WebSocketCoreImpl
import io.homeassistant.companion.android.common.data.websocket.impl.entities.SocketResponse
import io.homeassistant.companion.android.database.server.Server
import javax.inject.Inject
import javax.inject.Provider
import kotlinx.coroutines.flow.Flow
import okhttp3.OkHttpClient

/**
 * This interface provides a low-level abstraction for interacting with the Home Assistant WebSocket API.
 * **Key Responsibilities:**
 *   - Establishing and maintaining a WebSocket connection.
 *   - Handling authentication with Home Assistant.
 *   - Sending and receiving structured and raw messages.
 *   - Subscribing to and receiving events.
 *   - Managing the WebSocket connection lifecycle.
 *
 * Implementations of this interface handle the underlying network communication and basic message serialization/deserialization.
 *
 * It is meant to stay as minimal as possible to ease the maintenance of this complicated piece.
 *
 * All the method are thread safe.
 */
internal interface WebSocketCore {
    suspend fun connect(): Boolean
    fun getConnectionState(): WebSocketState?
    suspend fun sendMessage(request: Map<*, *>): SocketResponse?
    suspend fun sendMessage(request: WebSocketRequest): SocketResponse?
    suspend fun sendBytes(data: ByteArray): Boolean?
    suspend fun <T : Any> subscribeTo(
        type: String,
        data: Map<Any, Any> = mapOf(),
        timeout: kotlin.time.Duration = kotlin.time.Duration.ZERO,
    ): Flow<T>?

    fun shutdown()

    fun server(): Server?
}

internal class WebSocketCoreFactory @Inject constructor(
    private val okHttpClient: OkHttpClient,
    // Use a Provider to avoid a dependency circle since serverManager needs WebSocketCoreFactory
    private val serverManagerProvider: Provider<ServerManager>,
) {

    fun create(serverId: Int): WebSocketCore {
        return WebSocketCoreImpl(okHttpClient, serverManagerProvider.get(), serverId)
    }
}
