package io.homeassistant.companion.android.common.data.websocket.impl

import android.util.Log
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.homeassistant.companion.android.common.data.authentication.AuthenticationRepository
import io.homeassistant.companion.android.common.data.url.UrlRepository
import io.homeassistant.companion.android.common.data.websocket.WebSocketRepository
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.internal.notify
import okio.ByteString
import javax.inject.Inject

class WebSocketRepositoryImpl @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val urlRepository: UrlRepository,
    private val authenticationRepository: AuthenticationRepository
) : WebSocketRepository, WebSocketListener() {

    companion object {
        private const val TAG = "WebSocketRepository"
    }

    private val ioScope = CoroutineScope(Dispatchers.IO + Job())
    private val mapper = jacksonObjectMapper()
    private val callbacks = mutableMapOf<Int, (Boolean) -> Unit>()
    private var id = 1
    private var connection: WebSocket? = null
    private var connected = Job()


    override suspend fun sendPing(callback: (successful: Boolean) -> Unit) {
        connect()

        val id = getNextId()

        callbacks[id] = callback

        connection!!.send(
            mapper.writeValueAsString(
                mapOf(
                    "id" to id,
                    "type" to "ping"
                )
            )
        )
    }

    @Synchronized
    private fun getNextId(): Int {
        return id++
    }


    /**
     * This method will
     */
    @Synchronized
    private suspend fun connect() {
        if (connection != null && connected.isCompleted) {
            return
        }

        val url = urlRepository.getUrl() ?: return
        val urlString = url.toString()
            .replace("https://", "wss://")
            .replace("http://", "ws://")
            .plus("api/websocket")

        connection = okHttpClient.newWebSocket(
            Request.Builder().url(urlString).build(),
            this
        )

        connected.join()

    }

    private suspend fun handleAuth() {
        connection!!.send(
            mapper.writeValueAsString(
                mapOf(
                    "type" to "auth",
                    "access_token" to authenticationRepository.retrieveAccessToken()
                )
            )
        )
    }

    private fun handleAuthSuccess() {
        connected.complete()
    }

    private fun handlePong(response: Map<String, Any>) {
        val id = response["id"] as Int
        callbacks[id]?.invoke(true)
        callbacks.remove(id)
    }

    override fun onOpen(webSocket: WebSocket, response: Response) {
        Log.d(TAG, "Websocket: onOpen")
    }

    override fun onMessage(webSocket: WebSocket, text: String) {
        Log.d(TAG, "Websocket: onMessage (text)")
        val message: Map<String, Any> = mapper.readValue(text)

        ioScope.launch {
            when (message["type"] as? String) {
                "auth_required" -> handleAuth()
                "auth_ok" -> handleAuthSuccess()
                "pong" -> handlePong(message)
                else -> Log.d(TAG, "Unknown message type: $text")
            }
        }
    }

    override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
        Log.d(TAG, "Websocket: onMessage (bytes)")
    }

    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
        Log.d(TAG, "Websocket: onClosing")
    }

    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
        Log.d(TAG, "Websocket: onClosed")
        connected = Job()
        connection = null
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        Log.d(TAG, "Websocket: onFailure")
    }
}
