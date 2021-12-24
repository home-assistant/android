package io.homeassistant.companion.android.common.data.websocket.impl

import android.util.Log
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.DeserializationFeature
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
import io.homeassistant.companion.android.common.data.websocket.impl.entities.EventResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.GetConfigResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.SocketResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.StateChangedEvent
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
    private val responseCallbackJobs = mutableMapOf<Long, CancellableContinuation<SocketResponse>>()
    private val id = AtomicLong(1)
    private var connection: WebSocket? = null
    private val connectedMutex = Mutex()
    private var connected = Job()
    private val stateChangedMutex = Mutex()
    private var stateChangedFlow: SharedFlow<StateChangedEvent>? = null

    @ExperimentalCoroutinesApi
    private var producerScope: ProducerScope<StateChangedEvent>? = null

    override suspend fun sendPing(): Boolean {
        val socketResponse = sendMessage(
            mapOf(
                "type" to "ping"
            )
        )

        return socketResponse.type == "pong"
    }

    override suspend fun getConfig(): GetConfigResponse? {
        return try {
            val socketResponse = sendMessage(
                mapOf(
                    "type" to "get_config"
                )
            )

            mapper.convertValue(socketResponse.result!!, GetConfigResponse::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "Unable to get config response", e)
            null
        }
    }

    override suspend fun getStates(): List<EntityResponse<Any>> {
        return try {
            val socketResponse = sendMessage(
                mapOf(
                    "type" to "get_states"
                )
            )

            mapper.convertValue(
                socketResponse.result!!,
                object : TypeReference<List<EntityResponse<Any>>>() {}
            )
        } catch (e: Exception) {
            Log.e(TAG, "Unable to get list of entities", e)
            emptyList()
        }
    }

    override suspend fun getServices(): List<DomainResponse> {
        return try {
            val socketResponse = sendMessage(
                mapOf(
                    "type" to "get_services"
                )
            )

            val response = mapper.convertValue(
                socketResponse.result!!,
                object : TypeReference<Map<String, Map<String, ServiceData>>>() {}
            )

            response.map {
                DomainResponse(it.key, it.value)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Unable to get service data")
            emptyList()
        }
    }

    override suspend fun getPanels(): List<String> {
        TODO("Not yet implemented")
    }

    override suspend fun callService(request: ServiceCallRequest) {
        TODO("Not yet implemented")
    }

    @ExperimentalCoroutinesApi
    override suspend fun getStateChanges(): Flow<StateChangedEvent> {
        try {
            stateChangedMutex.withLock {
                if (stateChangedFlow == null) {

                    val response = sendMessage(
                        mapOf(
                            "type" to "subscribe_events",
                            "event_type" to "state_changed"
                        )
                    )

                    stateChangedFlow = callbackFlow {
                        producerScope = this
                        awaitClose {
                            Log.d(TAG, "Unsubscribing from state_changes")
                            ioScope.launch {
                                sendMessage(
                                    mapOf(
                                        "type" to "unsubscribe_events",
                                        "subscription" to response.id
                                    )
                                )
                            }
                            producerScope = null
                            stateChangedFlow = null
                        }
                    }.shareIn(ioScope, SharingStarted.WhileSubscribed())
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Unable to get flow of entities", e)
        }
        return emptyFlow()
    }

    /**
     * This method will
     */
    private suspend fun connect() {
        connectedMutex.withLock {
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
        if (connection != null) {
            connection!!.send(
                mapper.writeValueAsString(
                    mapOf(
                        "type" to "auth",
                        "access_token" to authenticationRepository.retrieveAccessToken()
                    )
                )
            )
        } else
            Log.e(TAG, "Attempted to authenticate when connection is null")
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

    @ExperimentalCoroutinesApi
    private suspend fun handleEvent(response: SocketResponse) {
        val eventResponse = mapper.convertValue(
            response.event,
            object : TypeReference<EventResponse>() {}
        )
        producerScope?.send(eventResponse.data)
    }

    @ExperimentalCoroutinesApi
    private fun handleClosingSocket() {
        connected = Job()
        connection = null
        // If we still have flows flowing
        if (stateChangedFlow != null && ioScope.isActive) {
            ioScope.launch {
                try {
                    connect()
                    // Register for websocket events!
                    sendMessage(
                        mapOf(
                            "type" to "subscribe_events",
                            "event_type" to "state_changed"
                        )
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Issue reconnecting websocket", e)
                }
            }
        }
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
                "event" -> handleEvent(message)
                else -> Log.d(TAG, "Unknown message type: $text")
            }
        }
    }

    override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
        Log.d(TAG, "Websocket: onMessage (bytes)")
    }

    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
        Log.d(TAG, "Websocket: onClosing code: $code, reason: $reason")
        handleClosingSocket()
    }

    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
        Log.d(TAG, "Websocket: onClosed")
        handleClosingSocket()
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        Log.e(TAG, "Websocket: onFailure", t)
        handleClosingSocket()
    }
}
