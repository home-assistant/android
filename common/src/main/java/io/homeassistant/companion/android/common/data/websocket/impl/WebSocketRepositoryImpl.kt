package io.homeassistant.companion.android.common.data.websocket.impl

import android.util.Log
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.homeassistant.companion.android.common.data.authentication.AuthenticationRepository
import io.homeassistant.companion.android.common.data.integration.ServiceData
import io.homeassistant.companion.android.common.data.integration.impl.entities.EntityResponse
import io.homeassistant.companion.android.common.data.integration.impl.entities.ServiceCallRequest
import io.homeassistant.companion.android.common.data.url.UrlRepository
import io.homeassistant.companion.android.common.data.websocket.WebSocketRepository
import io.homeassistant.companion.android.common.data.websocket.impl.entities.DomainResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.GetConfigResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.SocketResponse
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.util.concurrent.atomic.AtomicLong
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
        .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
    private val responseCallbackJobs = mutableMapOf<Long, CancellableContinuation<SocketResponse>>()
    private val subscriptionCallbacks = mutableMapOf<Long, (Boolean) -> Unit>()
    private val id = AtomicLong(1)
    private var connection: WebSocket? = null
    private var connected = Job()

    override suspend fun sendPing(): Boolean {
        val socketResponse = sendMessage(
            mapOf(
                "type" to "ping"
            )
        )

        return socketResponse.type == "pong"
    }

    override suspend fun getConfig(): GetConfigResponse {
        val socketResponse = sendMessage(
            mapOf(
                "type" to "get_config"
            )
        )

        return mapper.convertValue(socketResponse.result!!, GetConfigResponse::class.java)
    }

    override suspend fun getStates(): List<EntityResponse<Any>> {
        val socketResponse = sendMessage(
            mapOf(
                "type" to "get_states"
            )
        )

        return mapper.convertValue(
            socketResponse.result!!,
            object : TypeReference<List<EntityResponse<Any>>>() {}
        )
    }

    override suspend fun getServices(): List<DomainResponse> {
        val socketResponse = sendMessage(
            mapOf(
                "type" to "get_services"
            )
        )

        val response = mapper.convertValue(
            socketResponse.result!!,
            object : TypeReference<Map<String, Map<String, ServiceData>>>() {}
        )

        return response.map {
            DomainResponse(it.key, it.value)
        }
    }

    override suspend fun getPanels(): List<String> {
        TODO("Not yet implemented")
    }

    override suspend fun callService(request: ServiceCallRequest) {
        TODO("Not yet implemented")
    }

    /**
     * This method will
     */
    @Synchronized
    private suspend fun connect() {
        if (connection != null && connected.isCompleted) {
            return
        }

        val url = urlRepository.getUrl() ?: throw Exception("Unable to get URL for WebSocket")
        val urlString = url.toString()
            .replace("https://", "wss://")
            .replace("http://", "ws://")
            .plus("api/websocket")

        connection = okHttpClient.newWebSocket(
            Request.Builder().url(urlString).build(),
            this
        )

        // Preemptively send auth
        authenticate()

        // Wait up to 30 seconds for auth response
        withTimeout(30000) {
            connected.join()
        }
    }

    private suspend fun sendMessage(request: Map<*, *>): SocketResponse {
        for (i in 0..1) {
            val requestId = id.getAndIncrement()
            val outbound = request.plus("id" to requestId)
            Log.d(TAG, "Sending message number $requestId: $outbound")
            connect()
            try {
                return withTimeout(30000) {
                    suspendCancellableCoroutine { cont ->
                        responseCallbackJobs[requestId] = cont
                        connection!!.send(mapper.writeValueAsString(outbound))
                        Log.d(TAG, "Message number $requestId sent")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error sending request number $requestId", e)
            }
        }
        throw Exception("Unable to send message: $request")
    }

    private suspend fun authenticate() {
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
        Log.d(TAG, "Message number ${message.id} received: $text")

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
        Log.d(TAG, "Websocket: onClosing code: $code, reason: $reason")
        connected = Job()
        connection = null
    }

    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
        Log.d(TAG, "Websocket: onClosed")
        connected = Job()
        connection = null
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        Log.d(TAG, "Websocket: onFailure")
        Log.e(TAG, "Failure in websocket", t)
    }
}
