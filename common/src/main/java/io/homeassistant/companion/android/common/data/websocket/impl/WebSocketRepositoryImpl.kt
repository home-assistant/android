package io.homeassistant.companion.android.common.data.websocket.impl

import android.util.Log
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.module.kotlin.contains
import com.fasterxml.jackson.module.kotlin.convertValue
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.homeassistant.companion.android.common.BuildConfig
import io.homeassistant.companion.android.common.data.HomeAssistantApis.Companion.USER_AGENT
import io.homeassistant.companion.android.common.data.HomeAssistantApis.Companion.USER_AGENT_STRING
import io.homeassistant.companion.android.common.data.HomeAssistantVersion
import io.homeassistant.companion.android.common.data.authentication.AuthenticationRepository
import io.homeassistant.companion.android.common.data.authentication.AuthorizationException
import io.homeassistant.companion.android.common.data.integration.ServiceData
import io.homeassistant.companion.android.common.data.integration.impl.entities.EntityResponse
import io.homeassistant.companion.android.common.data.url.UrlRepository
import io.homeassistant.companion.android.common.data.websocket.WebSocketRepository
import io.homeassistant.companion.android.common.data.websocket.WebSocketRequest
import io.homeassistant.companion.android.common.data.websocket.WebSocketState
import io.homeassistant.companion.android.common.data.websocket.impl.entities.AreaRegistryResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.AreaRegistryUpdatedEvent
import io.homeassistant.companion.android.common.data.websocket.impl.entities.CompressedStateChangedEvent
import io.homeassistant.companion.android.common.data.websocket.impl.entities.ConversationResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.DeviceRegistryResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.DeviceRegistryUpdatedEvent
import io.homeassistant.companion.android.common.data.websocket.impl.entities.DomainResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.EntityRegistryResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.EntityRegistryUpdatedEvent
import io.homeassistant.companion.android.common.data.websocket.impl.entities.EventResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.GetConfigResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.SocketResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.StateChangedEvent
import io.homeassistant.companion.android.common.data.websocket.impl.entities.TemplateUpdatedEvent
import io.homeassistant.companion.android.common.data.websocket.impl.entities.TriggerEvent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.consumeAsFlow
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
import java.io.IOException
import java.util.Collections
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import kotlin.coroutines.resumeWithException

class WebSocketRepositoryImpl @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val urlRepository: UrlRepository,
    private val authenticationRepository: AuthenticationRepository
) : WebSocketRepository, WebSocketListener() {

    companion object {
        private const val TAG = "WebSocketRepository"

        private const val SUBSCRIBE_TYPE_SUBSCRIBE_EVENTS = "subscribe_events"
        private const val SUBSCRIBE_TYPE_SUBSCRIBE_ENTITIES = "subscribe_entities"
        private const val SUBSCRIBE_TYPE_SUBSCRIBE_TRIGGER = "subscribe_trigger"
        private const val SUBSCRIBE_TYPE_RENDER_TEMPLATE = "render_template"
        private const val SUBSCRIBE_TYPE_PUSH_NOTIFICATION_CHANNEL =
            "mobile_app/push_notification_channel"
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
    private val activeMessages = Collections.synchronizedMap(mutableMapOf<Long, WebSocketRequest>())
    private val id = AtomicLong(1)
    private var connection: WebSocket? = null
    private var connectionState: WebSocketState? = null
    private var connectionHaVersion: HomeAssistantVersion? = null
    private val connectedMutex = Mutex()
    private var connected = CompletableDeferred<Boolean>()
    private val eventSubscriptionMutex = Mutex()

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

    override suspend fun getConversation(speech: String): ConversationResponse? {
        // TODO: Send default locale of device with request.
        val socketResponse = sendMessage(
            mapOf(
                "type" to "conversation/process",
                "text" to speech
            )
        )

        return mapResponse(socketResponse)
    }

    override suspend fun getStateChanges(): Flow<StateChangedEvent>? =
        subscribeToEventsForType(EVENT_STATE_CHANGED)

    override suspend fun getStateChanges(entityIds: List<String>): Flow<TriggerEvent>? =
        subscribeToTrigger("state", mapOf("entity_id" to entityIds))

    override suspend fun getCompressedStateAndChanges(): Flow<CompressedStateChangedEvent>? =
        subscribeTo(SUBSCRIBE_TYPE_SUBSCRIBE_ENTITIES)

    override suspend fun getCompressedStateAndChanges(entityIds: List<String>): Flow<CompressedStateChangedEvent>? =
        subscribeTo(SUBSCRIBE_TYPE_SUBSCRIBE_ENTITIES, mapOf("entity_ids" to entityIds))

    override suspend fun getAreaRegistryUpdates(): Flow<AreaRegistryUpdatedEvent>? =
        subscribeToEventsForType(EVENT_AREA_REGISTRY_UPDATED)

    override suspend fun getDeviceRegistryUpdates(): Flow<DeviceRegistryUpdatedEvent>? =
        subscribeToEventsForType(EVENT_DEVICE_REGISTRY_UPDATED)

    override suspend fun getEntityRegistryUpdates(): Flow<EntityRegistryUpdatedEvent>? =
        subscribeToEventsForType(EVENT_ENTITY_REGISTRY_UPDATED)

    private suspend fun <T : Any> subscribeToEventsForType(eventType: String): Flow<T>? =
        subscribeTo(SUBSCRIBE_TYPE_SUBSCRIBE_EVENTS, mapOf("event_type" to eventType))

    override suspend fun getTemplateUpdates(template: String): Flow<TemplateUpdatedEvent>? =
        subscribeTo(SUBSCRIBE_TYPE_RENDER_TEMPLATE, mapOf("template" to template))

    private suspend fun subscribeToTrigger(platform: String, data: Map<Any, Any>): Flow<TriggerEvent>? {
        val triggerData = mapOf(
            "platform" to platform
        ).plus(data)
        return subscribeTo(SUBSCRIBE_TYPE_SUBSCRIBE_TRIGGER, mapOf("trigger" to triggerData))
    }

    /**
     * Start a subscription for events on the websocket connection and get a Flow for listening to
     * new messages. When there are no more listeners, the subscription will automatically be cancelled
     * using `unsubscribe_events`. If the subscription already exists, the existing Flow is returned.
     *
     * @param type value for the `type` key in the subscription message, for example `subscribe_events`
     * @param data a key/value map of additional data to be included in the subscription message, for
     *             example the `event_type` + value when subscribing with `subscribe_events`
     * @param timeout timeout until the subscription is ended after the flow is no longer collected
     * @return a Flow that will emit messages delivered to this subscription, or `null` if an error
     *         occurred
     */
    private suspend fun <T : Any> subscribeTo(
        type: String,
        data: Map<Any, Any> = mapOf(),
        timeout: Long = 0
    ): Flow<T>? {
        val subscribeMessage = mapOf(
            "type" to type
        ).plus(data)

        eventSubscriptionMutex.withLock {
            val isNewMessage = synchronized(activeMessages) {
                activeMessages.values.none { it.message == subscribeMessage }
            }
            if (isNewMessage) {
                val channel = Channel<Any>(capacity = Channel.BUFFERED)
                val flow = callbackFlow<T> {
                    val producer = this as ProducerScope<Any>
                    launch {
                        channel.consumeAsFlow().collect {
                            producer.send(it)
                        }
                    }
                    awaitClose {
                        ioScope.launch {
                            var subscription: Long? = null
                            eventSubscriptionMutex.withLock {
                                synchronized(activeMessages) {
                                    activeMessages.entries.firstOrNull { it.value.message == subscribeMessage }
                                        ?.let {
                                            subscription = it.key
                                            channel.close()
                                            activeMessages.remove(subscription)
                                        }
                                }
                            }
                            subscription?.let {
                                Log.d(TAG, "Unsubscribing from $type with data $data")
                                sendMessage(
                                    mapOf(
                                        "type" to "unsubscribe_events",
                                        "subscription" to it
                                    )
                                )
                            }
                            if (activeMessages.isEmpty()) {
                                connection?.close(1001, "Done listening to subscriptions.")
                            }
                        }
                    }
                }.shareIn(ioScope, SharingStarted.WhileSubscribed(timeout, 0))

                val webSocketRequest = WebSocketRequest(
                    message = subscribeMessage,
                    eventFlow = flow,
                    onEvent = channel
                )
                val response = sendMessage(webSocketRequest)
                if (response == null || response.success != true) {
                    Log.e(TAG, "Unable to subscribe to $type with data $data")
                    synchronized(activeMessages) {
                        activeMessages.entries
                            .firstOrNull { it.value.message == subscribeMessage }
                            ?.let { activeMessages.remove(it.key) }
                    }
                    return null
                }
            }
        }
        return synchronized(activeMessages) {
            activeMessages.values.find { it.message == subscribeMessage }?.eventFlow as? Flow<T>
        }
    }

    override suspend fun getNotifications(): Flow<Map<String, Any>>? =
        subscribeTo(
            SUBSCRIBE_TYPE_PUSH_NOTIFICATION_CHANNEL,
            mapOf(
                "webhook_id" to urlRepository.getWebhookId().toString(),
                "support_confirm" to true
            ),
            DISCONNECT_DELAY
        )

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

    /**
     * Request the server to add a Matter device to the network and commission it
     * @return `true` if the request was successful
     */
    override suspend fun commissionMatterDevice(code: String): Boolean {
        val response = sendMessage(
            WebSocketRequest(
                message = mapOf(
                    "type" to "matter/commission",
                    "code" to code
                ),
                timeout = 120000L // Matter commissioning takes at least 60 seconds + interview
            )
        )

        return response?.success == true
    }

    /**
     * Request the server to commission a Matter device that is already on the network
     * @return `true` if the request was successful
     */
    override suspend fun commissionMatterDeviceOnNetwork(pin: Long): Boolean {
        val response = sendMessage(
            WebSocketRequest(
                message = mapOf(
                    "type" to "matter/commission_on_network",
                    "pin" to pin
                ),
                timeout = 120000L // Matter commissioning takes at least 60 seconds + interview
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

            try {
                connection = okHttpClient.newWebSocket(
                    Request.Builder().url(urlString).header(USER_AGENT, USER_AGENT_STRING).build(),
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
            } catch (e: Exception) {
                Log.e(TAG, "Unable to connect", e)
                return false
            }

            // Wait up to 30 seconds for auth response
            return true == withTimeoutOrNull(30000) {
                return@withTimeoutOrNull try {
                    val didConnect = connected.await()
                    if (didConnect && connectionHaVersion?.isAtLeast(2022, 9) == true) {
                        connection?.let {
                            val supportedFeaturesMessage = mapOf(
                                "type" to "supported_features",
                                "id" to id.getAndIncrement(),
                                "features" to mapOf(
                                    "coalesce_messages" to 1
                                )
                            )
                            Log.d(TAG, "Sending message ${supportedFeaturesMessage["id"]}: $supportedFeaturesMessage")
                            it.send(
                                mapper.writeValueAsString(supportedFeaturesMessage)
                            )
                        }
                    }
                    didConnect
                } catch (e: Exception) {
                    Log.e(TAG, "Unable to authenticate", e)
                    false
                }
            }
        }
    }

    private suspend fun sendMessage(request: Map<*, *>): SocketResponse? =
        sendMessage(WebSocketRequest(request))

    private suspend fun sendMessage(request: WebSocketRequest): SocketResponse? {
        return if (connect()) {
            withTimeoutOrNull(request.timeout) {
                try {
                    suspendCancellableCoroutine { cont ->
                        // Lock on the connection so that we fully send before allowing another send.
                        // This should prevent out of order errors.
                        connection?.let {
                            synchronized(it) {
                                val requestId = id.getAndIncrement()
                                val outbound = request.message.plus("id" to requestId)
                                Log.d(TAG, "Sending message $requestId: $outbound")
                                activeMessages[requestId] = request.apply {
                                    onResponse = cont
                                }
                                connection?.send(mapper.writeValueAsString(outbound))
                                Log.d(TAG, "Message number $requestId sent")
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Exception while sending message", e)
                    null
                }
            }
        } else {
            Log.e(TAG, "Unable to send message $request")
            null
        }
    }

    private inline fun <reified T> mapResponse(response: SocketResponse?): T? =
        if (response?.result != null) mapper.convertValue(response.result) else null

    private fun handleAuthComplete(successful: Boolean, haVersion: String?) {
        connectionHaVersion = haVersion?.let { HomeAssistantVersion.fromString(it) }
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
        activeMessages[id]?.let {
            it.onResponse?.let { cont ->
                if (cont.isActive) cont.resumeWith(Result.success(response))
            }
            if (it.eventFlow == null) {
                activeMessages.remove(id)
            }
        }
    }

    private suspend fun handleEvent(response: SocketResponse) {
        val subscriptionId = response.id
        activeMessages[subscriptionId]?.let { messageData ->
            val subscriptionType = messageData.message["type"]
            val eventResponseType = response.event?.get("event_type")

            val message: Any =
                if (response.event?.contains("hass_confirm_id") == true) {
                    mapper.convertValue(
                        response.event,
                        object : TypeReference<Map<String, Any>>() {}
                    )
                } else if (subscriptionType == SUBSCRIBE_TYPE_SUBSCRIBE_ENTITIES) {
                    mapper.convertValue(response.event, CompressedStateChangedEvent::class.java)
                } else if (subscriptionType == SUBSCRIBE_TYPE_RENDER_TEMPLATE) {
                    mapper.convertValue(response.event, TemplateUpdatedEvent::class.java)
                } else if (subscriptionType == SUBSCRIBE_TYPE_SUBSCRIBE_TRIGGER) {
                    val trigger = response.event?.get("variables")?.get("trigger")
                    if (trigger != null) {
                        mapper.convertValue(trigger, TriggerEvent::class.java)
                    } else {
                        Log.w(TAG, "Received no trigger value for trigger subscription, skipping")
                        return
                    }
                } else if (eventResponseType != null && eventResponseType.isTextual) {
                    val eventResponseClass = when (eventResponseType.textValue()) {
                        EVENT_STATE_CHANGED ->
                            object :
                                TypeReference<EventResponse<StateChangedEvent>>() {}
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

                    mapper.convertValue(
                        response.event,
                        eventResponseClass
                    ).data
                } else {
                    Log.d(TAG, "Unknown event for subscription received, skipping")
                    return
                }

            try {
                messageData.onEvent?.send(message)
            } catch (e: Exception) {
                Log.e(TAG, "Unable to send event message to channel", e)
            }
        } ?: run {
            Log.d(TAG, "Received event for unknown subscription, unsubscribing")
            sendMessage(
                mapOf(
                    "type" to "unsubscribe_events",
                    "subscription" to subscriptionId
                )
            )
        }
    }

    private fun handleClosingSocket() {
        ioScope.launch {
            connectedMutex.withLock {
                connected = CompletableDeferred()
                connection = null
                connectionHaVersion = null
                if (connectionState != WebSocketState.CLOSED_AUTH)
                    connectionState = WebSocketState.CLOSED_OTHER
                synchronized(activeMessages) {
                    activeMessages
                        .filterValues { it.eventFlow == null }
                        .forEach {
                            it.value.onResponse?.let { cont ->
                                if (cont.isActive) cont.resumeWithException(IOException())
                            }
                            activeMessages.remove(it.key)
                        }
                }
            }
        }
        // If we still have flows flowing
        val hasFlowMessages = synchronized(activeMessages) {
            activeMessages.any { it.value.eventFlow != null }
        }
        if (hasFlowMessages && ioScope.isActive) {
            ioScope.launch {
                delay(10000)
                if (connect()) {
                    Log.d(TAG, "Resubscribing to active subscriptions...")
                    synchronized(activeMessages) {
                        activeMessages.filterValues { it.eventFlow != null }.entries
                    }.forEach { (oldId, oldMessage) ->
                        val response = sendMessage(oldMessage)
                        if (response == null || response.success != true) {
                            Log.e(TAG, "Issue re-registering subscription with ${oldMessage.message}")
                        } else {
                            // sendMessage will have created a new item for this subscription
                            activeMessages.remove(oldId)
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
        Log.d(TAG, "Websocket: onMessage (${if (BuildConfig.DEBUG) "text: $text" else "text"})")
        val textTree = mapper.readTree(text)
        val messages: List<SocketResponse> = if (textTree.isArray) {
            textTree.elements().asSequence().toList().map { mapper.convertValue(it) }
        } else {
            listOf(mapper.readValue(text))
        }

        messages.forEach { message ->
            Log.d(TAG, "Message number ${message.id} received")

            ioScope.launch {
                when (message.type) {
                    "auth_required" -> Log.d(TAG, "Auth Requested")
                    "auth_ok" -> handleAuthComplete(true, message.haVersion)
                    "auth_invalid" -> handleAuthComplete(false, message.haVersion)
                    "pong", "result" -> handleMessage(message)
                    "event" -> handleEvent(message)
                    else -> Log.d(TAG, "Unknown message type: ${message.type}")
                }
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
