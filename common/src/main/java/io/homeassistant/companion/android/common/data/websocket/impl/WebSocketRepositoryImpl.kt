package io.homeassistant.companion.android.common.data.websocket.impl

import android.util.Log
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.module.kotlin.contains
import com.fasterxml.jackson.module.kotlin.convertValue
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.homeassistant.companion.android.common.data.authentication.AuthenticationRepository
import io.homeassistant.companion.android.common.data.authentication.AuthorizationException
import io.homeassistant.companion.android.common.data.integration.ServiceData
import io.homeassistant.companion.android.common.data.integration.impl.entities.EntityResponse
import io.homeassistant.companion.android.common.data.url.UrlRepository
import io.homeassistant.companion.android.common.data.websocket.WebSocketRepository
import io.homeassistant.companion.android.common.data.websocket.WebSocketState
import io.homeassistant.companion.android.common.data.websocket.impl.entities.AreaRegistryResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.AreaRegistryUpdatedEvent
import io.homeassistant.companion.android.common.data.websocket.impl.entities.DeviceRegistryResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.DeviceRegistryUpdatedEvent
import io.homeassistant.companion.android.common.data.websocket.impl.entities.DomainResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.EntityRegistryResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.EntityRegistryUpdatedEvent
import io.homeassistant.companion.android.common.data.websocket.impl.entities.EventResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.GetConfigResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.SocketResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.StateChangedEvent
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
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

        private const val EVENT_STATE_CHANGED = "state_changed"
        private const val EVENT_AREA_REGISTRY_UPDATED = "area_registry_updated"
        private const val EVENT_DEVICE_REGISTRY_UPDATED = "device_registry_updated"
        private const val EVENT_ENTITY_REGISTRY_UPDATED = "entity_registry_updated"

        private const val DISCONNECT_DELAY = 10000L
    }

    private val ioScope = CoroutineScope(Dispatchers.IO + Job())
    private val mapper = jacksonObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
    private val responseCallbackJobs = mutableMapOf<Long, CancellableContinuation<SocketResponse>>()
    private val id = AtomicLong(1)
    private var connection: WebSocket? = null
    private var connectionState: WebSocketState? = null
    private val connectedMutex = Mutex()
    private var connected = CompletableDeferred<Boolean>()
    private val eventSubscriptionMutex = Mutex()
    private var eventSubscriptionId = mutableMapOf<Map<Any, Any>, Long?>()
    private val eventSubscriptionFlow = mutableMapOf<Map<Any, Any>, SharedFlow<*>>()
    private var eventSubscriptionProducerScope = mutableMapOf<Map<Any, Any>, ProducerScope<Any>>()
    private val notificationMutex = Mutex()
    private var notificationFlow: Flow<Map<String, Any>>? = null
    private var notificationProducerScope: ProducerScope<Map<String, Any>>? = null

    override fun getConnectionState(): WebSocketState? = connectionState

    override suspend fun sendPing(): Boolean {
        val socketResponse = sendMessage(
            mapOf(
                "type" to "ping"
            )
        )

        return socketResponse?.type == "pong"
    }

    override suspend fun getConfig(): GetConfigResponse? {
        val socketResponse = sendMessage(
            mapOf(
                "type" to "get_config"
            )
        )

        return mapResponse(socketResponse)
    }

    override suspend fun getStates(): List<EntityResponse<Any>>? {
        val socketResponse = sendMessage(
            mapOf(
                "type" to "get_states"
            )
        )

        return mapResponse(socketResponse)
    }

    override suspend fun getAreaRegistry(): List<AreaRegistryResponse>? {

        val socketResponse = sendMessage(
            mapOf(
                "type" to "config/area_registry/list"
            )
        )

        return mapResponse(socketResponse)
    }

    override suspend fun getDeviceRegistry(): List<DeviceRegistryResponse>? {

        val socketResponse = sendMessage(
            mapOf(
                "type" to "config/device_registry/list"
            )
        )

        return mapResponse(socketResponse)
    }

    override suspend fun getEntityRegistry(): List<EntityRegistryResponse>? {

        val socketResponse = sendMessage(
            mapOf(
                "type" to "config/entity_registry/list"
            )
        )

        return mapResponse(socketResponse)
    }

    override suspend fun getServices(): List<DomainResponse>? {
        val socketResponse = sendMessage(
            mapOf(
                "type" to "get_services"
            )
        )

        val response: Map<String, Map<String, ServiceData>>? = mapResponse(socketResponse)
        return response?.map {
            DomainResponse(it.key, it.value)
        }
    }

    override suspend fun getStateChanges(): Flow<StateChangedEvent>? =
        subscribeToEventsForType(EVENT_STATE_CHANGED)

    override suspend fun getAreaRegistryUpdates(): Flow<AreaRegistryUpdatedEvent>? =
        subscribeToEventsForType(EVENT_AREA_REGISTRY_UPDATED)

    override suspend fun getDeviceRegistryUpdates(): Flow<DeviceRegistryUpdatedEvent>? =
        subscribeToEventsForType(EVENT_DEVICE_REGISTRY_UPDATED)

    override suspend fun getEntityRegistryUpdates(): Flow<EntityRegistryUpdatedEvent>? =
        subscribeToEventsForType(EVENT_ENTITY_REGISTRY_UPDATED)

    private suspend fun <T : Any> subscribeToEventsForType(eventType: String): Flow<T>? =
        subscribeTo("subscribe_events", mapOf("event_type" to eventType))

    /**
     * Start a subscription for events on the websocket connection and get a Flow for listening to
     * new messages. When there are no more listeners, the subscription will automatically be cancelled
     * using `unsubscribe_events`. If the subscription already exists, the existing Flow is returned.
     *
     * @param type value for the `type` key in the subscription message, for example `subscribe_events`
     * @param data a key/value map of additional data to be included in the subscription message, for
     *             example the `event_type` + value when subscribing with `subscribe_events`
     * @return a Flow that will emit messages delivered to this subscription, or `null` if an error
     *         occurred
     */
    private suspend fun <T : Any> subscribeTo(type: String, data: Map<Any, Any>): Flow<T>? {
        val subscribeMessage = mapOf(
            "type" to type
        ).plus(data)

        eventSubscriptionMutex.withLock {
            if (eventSubscriptionId[subscribeMessage] == null) {

                val response = sendMessage(subscribeMessage)
                eventSubscriptionId[subscribeMessage] = response?.id
                if (response == null) {
                    Log.e(TAG, "Unable to subscribe to $type with data $data")
                    return null
                }

                // Subscriptions are stored by subscribe message instead of ID, because the ID will
                // change when the app needs to resubscribe
                eventSubscriptionFlow[subscribeMessage] = callbackFlow<T> {
                    eventSubscriptionProducerScope[subscribeMessage] = this as ProducerScope<Any>
                    awaitClose {
                        Log.d(TAG, "Unsubscribing from $type with data $data")
                        ioScope.launch {
                            sendMessage(
                                mapOf(
                                    "type" to "unsubscribe_events",
                                    "subscription" to eventSubscriptionId[subscribeMessage]!!
                                )
                            )
                            eventSubscriptionId.remove(subscribeMessage)
                        }
                        eventSubscriptionProducerScope.remove(subscribeMessage)
                        eventSubscriptionFlow.remove(subscribeMessage)
                    }
                }.shareIn(ioScope, SharingStarted.WhileSubscribed())
            }
        }
        return eventSubscriptionFlow[subscribeMessage]!! as Flow<T>
    }

    private fun getSubscriptionMessageById(id: Long): Map<Any, Any>? =
        eventSubscriptionId.filterValues { it == id }.keys.firstOrNull()

    override suspend fun getNotifications(): Flow<Map<String, Any>>? {
        notificationMutex.withLock {
            if (notificationFlow == null) {
                val response = sendMessage(
                    mapOf(
                        "type" to "mobile_app/push_notification_channel",
                        "webhook_id" to urlRepository.getWebhookId(),
                        "support_confirm" to true
                    )
                )

                if (response == null) {
                    Log.e(TAG, "Unable to register for notifications")
                    return null
                }

                notificationFlow = callbackFlow {
                    notificationProducerScope = this
                    awaitClose {
                        // TODO: Is there a way to unsubscribe?
                        notificationFlow = null
                        notificationProducerScope = null
                        connection?.close(1001, "Done listening to notifications.")
                    }
                }.shareIn(ioScope, SharingStarted.WhileSubscribed(DISCONNECT_DELAY, 0))
            }

            return notificationFlow
        }
    }

    override suspend fun ackNotification(confirmId: String): Boolean {
        val response = sendMessage(
            mapOf(
                "type" to "mobile_app/push_notification_confirm",
                "webhook_id" to urlRepository.getWebhookId(),
                "confirm_id" to confirmId
            )
        )
        return response?.success == true
    }

    private suspend fun connect(): Boolean {
        connectedMutex.withLock {
            if (connection != null && connected.isCompleted) {
                return !connected.isCancelled
            }

            val url = urlRepository.getUrl()
            if (url == null) {
                Log.w(TAG, "No url to connect websocket too.")
                return false
            }

            val urlString = url.toString()
                .replace("https://", "wss://")
                .replace("http://", "ws://")
                .plus("api/websocket")

            connection = okHttpClient.newWebSocket(
                Request.Builder().url(urlString).build(),
                this
            ).also {
                // Preemptively send auth
                connectionState = WebSocketState.AUTHENTICATING
                it.send(
                    mapper.writeValueAsString(
                        mapOf(
                            "type" to "auth",
                            "access_token" to authenticationRepository.retrieveAccessToken()
                        )
                    )
                )
            }

            // Wait up to 30 seconds for auth response
            return true == withTimeoutOrNull(30000) {
                return@withTimeoutOrNull try {
                    connected.await()
                } catch (e: Exception) {
                    Log.e(TAG, "Unable to authenticate", e)
                    false
                }
            }
        }
    }

    private suspend fun sendMessage(request: Map<*, *>): SocketResponse? {
        return if (connect()) {
            withTimeoutOrNull(30000) {
                suspendCancellableCoroutine { cont ->
                    // Lock on the connection so that we fully send before allowing another send.
                    // This should prevent out of order errors.
                    connection?.let {
                        synchronized(it) {
                            val requestId = id.getAndIncrement()
                            val outbound = request.plus("id" to requestId)
                            Log.d(TAG, "Sending message $requestId: $outbound")
                            responseCallbackJobs[requestId] = cont
                            connection?.send(mapper.writeValueAsString(outbound))
                            Log.d(TAG, "Message number $requestId sent")
                        }
                    }
                }
            }
        } else {
            Log.e(TAG, "Unable to send message $request")
            null
        }
    }

    private inline fun <reified T> mapResponse(response: SocketResponse?): T? =
        if (response?.result != null) mapper.convertValue(response.result) else null

    private fun handleAuthComplete(successful: Boolean) {
        if (successful) {
            connectionState = WebSocketState.ACTIVE
            connected.complete(true)
        } else {
            connectionState = WebSocketState.CLOSED_AUTH
            connected.completeExceptionally(AuthorizationException())
        }
    }

    private fun handleMessage(response: SocketResponse) {
        val id = response.id!!
        responseCallbackJobs[id]?.resumeWith(Result.success(response))
        responseCallbackJobs.remove(id)
    }

    private suspend fun handleEvent(response: SocketResponse) {
        val eventResponseType = response.event?.get("event_type")
        if (eventResponseType != null && eventResponseType.isTextual) {
            val eventResponseClass = when (eventResponseType.textValue()) {
                EVENT_STATE_CHANGED -> object : TypeReference<EventResponse<StateChangedEvent>>() {}
                EVENT_AREA_REGISTRY_UPDATED ->
                    object :
                        TypeReference<EventResponse<AreaRegistryUpdatedEvent>>() {}
                EVENT_DEVICE_REGISTRY_UPDATED ->
                    object :
                        TypeReference<EventResponse<DeviceRegistryUpdatedEvent>>() {}
                EVENT_ENTITY_REGISTRY_UPDATED ->
                    object :
                        TypeReference<EventResponse<EntityRegistryUpdatedEvent>>() {}
                else -> {
                    Log.d(TAG, "Unknown event type received")
                    object : TypeReference<EventResponse<Any>>() {}
                }
            }
            val eventResponse = mapper.convertValue(
                response.event,
                eventResponseClass
            )
            eventSubscriptionProducerScope[getSubscriptionMessageById(response.id!!)]?.send(eventResponse.data)
        } else if (response.event?.contains("hass_confirm_id") == true) {
            if (notificationProducerScope?.isActive == true) {
                notificationProducerScope?.send(
                    mapper.convertValue(
                        response.event,
                        object : TypeReference<Map<String, Any>>() {}
                    )
                )
            }
        }
    }

    private fun handleClosingSocket() {
        ioScope.launch {
            connectedMutex.withLock {
                connected = CompletableDeferred()
                connection = null
                if (connectionState != WebSocketState.CLOSED_AUTH)
                    connectionState = WebSocketState.CLOSED_OTHER
            }
        }
        // If we still have flows flowing
        if ((eventSubscriptionFlow.any() || notificationFlow != null) && ioScope.isActive) {
            ioScope.launch {
                delay(10000)
                if (connect()) {
                    eventSubscriptionFlow.forEach { (subscribeMessage, _) ->
                        val response = sendMessage(subscribeMessage)
                        eventSubscriptionId[subscribeMessage] = response?.id
                        if (response == null) {
                            Log.e(TAG, "Issue re-registering subscription with $subscribeMessage")
                        }
                    }
                    if (notificationFlow != null) {
                        val response = sendMessage(
                            mapOf(
                                "type" to "mobile_app/push_notification_channel",
                                "webhook_id" to urlRepository.getWebhookId(),
                                "support_confirm" to true
                            )
                        )

                        if (response == null) {
                            Log.e(TAG, "Unable to re-register for notifications")
                        }
                    }
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
        if (connected.isActive) {
            connected.completeExceptionally(t)
        }
        handleClosingSocket()
    }
}
