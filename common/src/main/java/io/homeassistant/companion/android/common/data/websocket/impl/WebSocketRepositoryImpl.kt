package io.homeassistant.companion.android.common.data.websocket.impl

import android.util.Log
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.PropertyNamingStrategy
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.homeassistant.companion.android.common.data.authentication.AuthenticationRepository
import io.homeassistant.companion.android.common.data.integration.impl.entities.DomainResponse
import io.homeassistant.companion.android.common.data.integration.impl.entities.EntityResponse
import io.homeassistant.companion.android.common.data.integration.impl.entities.GetConfigResponse
import io.homeassistant.companion.android.common.data.integration.impl.entities.ServiceCallRequest
import io.homeassistant.companion.android.common.data.url.UrlRepository
import io.homeassistant.companion.android.common.data.websocket.SocketResponse
import io.homeassistant.companion.android.common.data.websocket.WebSocketRepository
import kotlinx.coroutines.*
import okhttp3.*
import okio.ByteString
import java.lang.Exception
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
    private val mapper = jacksonObjectMapper().setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
    private val responseCallbackJobs = mutableMapOf<Long, CancellableContinuation<SocketResponse>>()
    private val subscriptionCallbacks = mutableMapOf<Long, (Boolean) -> Unit>()
    private var id = 1L
    private var connection: WebSocket? = null
    private var connected = Job()

    override suspend fun sendPing(): Boolean {
        connect()

        val id = getNextId()
        connection!!.send(
            mapper.writeValueAsString(
                mapOf(
                    "id" to id,
                    "type" to "ping"
                )
            )
        )

        val continuation = suspendCancellableCoroutine<Any> { cont -> responseCallbackJobs[id] = cont }

        return true
    }

    override suspend fun getConfig(): GetConfigResponse {
        connect()

        val id = getNextId()
        connection!!.send(
            mapper.writeValueAsString(
                mapOf(
                    "id" to id,
                    "type" to "get_config"
                )
            )
        )

        val socketResponse = suspendCancellableCoroutine<SocketResponse> { cont -> responseCallbackJobs[id] = cont }

        val result: GetConfigResponse = mapper.convertValue(socketResponse.result!!, GetConfigResponse::class.java)

        return result
    }

    override suspend fun getStates(): List<EntityResponse<Any>> {
        TODO("Not yet implemented")
    }

    override suspend fun getServices(): List<DomainResponse> {
        TODO("Not yet implemented")
    }

    override suspend fun getPanels(): List<String> {
        TODO("Not yet implemented")
    }

    override suspend fun callService(request: ServiceCallRequest) {
        TODO("Not yet implemented")
    }

    @Synchronized
    private fun getNextId(): Long {
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

        handleAuth()

        // Wait up to 30 seconds for auth
        withTimeout(30000) {
            connected.join()
        }

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

    private fun handleAuthComplete(successful: Boolean) {
        if (successful)
            connected.complete()
        else
            connected.completeExceptionally(Exception("Authentication Error"))
    }

    private fun handleMessage(response: SocketResponse) {
        val id = response.id!!
        responseCallbackJobs[id]?.resumeWith(Result.success(response))
        responseCallbackJobs.remove(id)
    }

    override fun onOpen(webSocket: WebSocket, response: Response) {
        Log.d(TAG, "Websocket: onOpen")
    }

    override fun onMessage(webSocket: WebSocket, text: String) {
        Log.d(TAG, "Websocket: onMessage (text)")
        val message: SocketResponse = mapper.readValue(text)

        ioScope.launch {
            when (message.type) {
                "auth_required" -> Log.d(TAG, "Auth Requested")
                "auth_ok" -> handleAuthComplete(true)
                "auth_invalid" -> handleAuthComplete(false)
                "pong", "result" -> handleMessage(message)
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
