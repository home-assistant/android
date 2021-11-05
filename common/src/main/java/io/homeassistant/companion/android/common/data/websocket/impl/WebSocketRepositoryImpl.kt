package io.homeassistant.companion.android.common.data.websocket.impl

import android.util.Log
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.homeassistant.companion.android.common.data.authentication.AuthenticationRepository
import io.homeassistant.companion.android.common.data.url.UrlRepository
import io.homeassistant.companion.android.common.data.websocket.WebSocketRepository
import kotlinx.coroutines.runBlocking
import okhttp3.*
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

    private val mapper = jacksonObjectMapper()
    private var webSocket: WebSocket? = null

    override suspend fun sendPing(): Boolean {
        connect()
        return true
    }

    private suspend fun handleAuth() {
        webSocket!!.send(
            mapper.writeValueAsString(
                mapOf(
                    "type" to "auth",
                    "access_token" to authenticationRepository.retrieveAccessToken()
                )
            )
        )
    }

    private suspend fun connect() {
        val url = urlRepository.getUrl() ?: return
        val urlString = url.toString()
            .replace("https://", "wss://")
            .replace("http://", "ws://")
            .plus("api/websocket")

        webSocket = okHttpClient.newWebSocket(
            Request.Builder().url(urlString).build(),
            this
        )
    }

    override fun onOpen(webSocket: WebSocket, response: Response) {
        Log.d(TAG, "Websocket: onOpen")
    }

    override fun onMessage(webSocket: WebSocket, text: String) {
        Log.d(TAG, "Websocket: onMessage (text)")
        val message: Map<String, Any> = mapper.readValue(text)

        runBlocking {
            when (message["type"] as? String) {
                "auth_required" -> handleAuth()
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
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        Log.d(TAG, "Websocket: onFailure")
    }
}
