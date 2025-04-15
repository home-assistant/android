package io.homeassistant.companion.android.common.data.websocket.impl

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.module.kotlin.contains
import com.fasterxml.jackson.module.kotlin.convertValue
import com.fasterxml.jackson.module.kotlin.readValue
import io.homeassistant.companion.android.common.BuildConfig
import io.homeassistant.companion.android.common.data.HomeAssistantApis.Companion.USER_AGENT
import io.homeassistant.companion.android.common.data.HomeAssistantApis.Companion.USER_AGENT_STRING
import io.homeassistant.companion.android.common.data.HomeAssistantVersion
import io.homeassistant.companion.android.common.data.authentication.AuthorizationException
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.data.websocket.WebSocketCore
import io.homeassistant.companion.android.common.data.websocket.WebSocketRequest
import io.homeassistant.companion.android.common.data.websocket.WebSocketState
import io.homeassistant.companion.android.common.data.websocket.impl.entities.AreaRegistryUpdatedEvent
import io.homeassistant.companion.android.common.data.websocket.impl.entities.AssistPipelineError
import io.homeassistant.companion.android.common.data.websocket.impl.entities.AssistPipelineEvent
import io.homeassistant.companion.android.common.data.websocket.impl.entities.AssistPipelineEventType
import io.homeassistant.companion.android.common.data.websocket.impl.entities.AssistPipelineIntentEnd
import io.homeassistant.companion.android.common.data.websocket.impl.entities.AssistPipelineIntentProgress
import io.homeassistant.companion.android.common.data.websocket.impl.entities.AssistPipelineIntentStart
import io.homeassistant.companion.android.common.data.websocket.impl.entities.AssistPipelineRunStart
import io.homeassistant.companion.android.common.data.websocket.impl.entities.AssistPipelineSttEnd
import io.homeassistant.companion.android.common.data.websocket.impl.entities.AssistPipelineTtsEnd
import io.homeassistant.companion.android.common.data.websocket.impl.entities.CompressedStateChangedEvent
import io.homeassistant.companion.android.common.data.websocket.impl.entities.DeviceRegistryUpdatedEvent
import io.homeassistant.companion.android.common.data.websocket.impl.entities.EntityRegistryUpdatedEvent
import io.homeassistant.companion.android.common.data.websocket.impl.entities.EventResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.SocketResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.StateChangedEvent
import io.homeassistant.companion.android.common.data.websocket.impl.entities.TemplateUpdatedEvent
import io.homeassistant.companion.android.common.data.websocket.impl.entities.TriggerEvent
import java.io.IOException
import java.util.Collections
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.resumeWithException
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.consumeEach
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
import okio.ByteString.Companion.toByteString
import timber.log.Timber

internal class WebSocketCoreImpl(
    private val okHttpClient: OkHttpClient,
    private val serverManager: ServerManager,
    private val serverId: Int,
) : WebSocketCore, WebSocketListener() {

    private val coroutineExceptionHandler = CoroutineExceptionHandler { ctx, err -> Timber.e(err, "Uncaught exception in WebSocketCoreImpl") }

    private val ioScope = CoroutineScope(Dispatchers.IO + Job() + coroutineExceptionHandler)

    private val activeMessages = Collections.synchronizedMap(mutableMapOf<Long, WebSocketRequest>())

    // Each message that we send needs a unique ID to match it to the answer
    private val id = AtomicLong(1)
    private var connection: WebSocket? = null
    private var connectionState: WebSocketState? = null
    private var connectionHaVersion: HomeAssistantVersion? = null
    private val connectedMutex = Mutex()

    /**
     * A [CompletableDeferred] that signals the establishment and authentication of a WebSocket connection.
     *
     * This deferred value completes with `true` upon successful connection and authentication.
     * It completes with an [Exception] if [onFailure] is invoked.
     * It completes with an [AuthorizationException] if the authentication fail.
     *
     * A new instance is created when the connection closed. It is not when the authentication fail because it
     * means it cannot recover without new credentials.
     */
    private var connected = CompletableDeferred<Boolean>()
    private val eventSubscriptionMutex = Mutex()

    private val messageQueue = Channel<Job>(capacity = Channel.UNLIMITED).apply {
        ioScope.launch {
            consumeEach { it.join() } // Run a job, and wait for it to complete before starting the next one
        }
    }

    override fun server() = serverManager.getServer(serverId)

    override suspend fun connect(): Boolean {
        connectedMutex.withLock {
            if (connection != null && connected.isCompleted) {
                return !connected.isCancelled
            }

            val url = server()?.connection?.getUrl()
            if (url == null) {
                Timber.w("No url to connect websocket too.")
                return false
            }

            val urlString = url.toString()
                .replace("https://", "wss://")
                .replace("http://", "ws://")
                .plus("api/websocket")

            try {
                connection = okHttpClient.newWebSocket(
                    Request.Builder().url(urlString).header(USER_AGENT, USER_AGENT_STRING).build(),
                    this,
                ).also {
                    // Preemptively send auth
                    connectionState = WebSocketState.AUTHENTICATING
                    it.send(
                        webSocketMapper.writeValueAsString(
                            mapOf(
                                "type" to "auth",
                                "access_token" to serverManager.authenticationRepository(serverId).retrieveAccessToken(),
                            ),
                        ),
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Unable to connect")
                return false
            }

            // Wait up to 30 seconds for auth response
            return true == withTimeoutOrNull(30.seconds) {
                return@withTimeoutOrNull try {
                    val didConnect = connected.await()
                    if (didConnect && connectionHaVersion?.isAtLeast(2022, 9) == true) {
                        connection?.let {
                            val supportedFeaturesMessage = mapOf(
                                "type" to "supported_features",
                                "id" to id.getAndIncrement(),
                                "features" to mapOf(
                                    "coalesce_messages" to 1,
                                ),
                            )
                            Timber.d("Sending message ${supportedFeaturesMessage["id"]}: $supportedFeaturesMessage")
                            it.send(
                                webSocketMapper.writeValueAsString(supportedFeaturesMessage),
                            )
                        }
                    }
                    didConnect
                } catch (e: Exception) {
                    Timber.e(e, "Unable to authenticate")
                    false
                }
            }
        }
    }

    override fun getConnectionState(): WebSocketState? = connectionState

    override suspend fun sendMessage(request: Map<*, *>): SocketResponse? =
        sendMessage(WebSocketRequest(request))

    override suspend fun sendMessage(request: WebSocketRequest): SocketResponse? {
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
                                Timber.d("Sending message $requestId: $outbound")
                                activeMessages[requestId] = request.apply {
                                    onResponse = cont
                                }
                                connection?.send(webSocketMapper.writeValueAsString(outbound))
                                Timber.d("Message number $requestId sent")
                            }
                        }
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Exception while sending message")
                    null
                }
            }
        } else {
            Timber.w("Unable to send message, not connected: $request")
            null
        }
    }

    override suspend fun sendBytes(data: ByteArray): Boolean? {
        return if (connect()) {
            withTimeoutOrNull(30.seconds) {
                try {
                    connection?.let {
                        synchronized(it) {
                            it.send(data.toByteString())
                        }
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Exception while sending bytes")
                    null
                }
            }
        } else {
            Timber.w("Unable to send bytes, not connected")
            null
        }
    }

    override suspend fun <T : Any> subscribeTo(
        type: String,
        data: Map<Any, Any>,
        timeout: kotlin.time.Duration,
    ): Flow<T>? {
        val subscribeMessage = mapOf(
            "type" to type,
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
                                Timber.d("Unsubscribing from $type with data $data")
                                sendMessage(
                                    mapOf(
                                        "type" to "unsubscribe_events",
                                        "subscription" to it,
                                    ),
                                )
                            }
                            if (activeMessages.isEmpty()) {
                                Timber.i("No more subscriptions, closing connection.")
                                connection?.close(1001, "Done listening to subscriptions.")
                            } else {
                                Timber.i("Still ${activeMessages.size} messages in the queue, not closing connection.")
                            }
                        }
                    }
                }.shareIn(ioScope, SharingStarted.WhileSubscribed(timeout.inWholeMilliseconds, 0))

                val webSocketRequest = WebSocketRequest(
                    message = subscribeMessage,
                    eventFlow = flow,
                    onEvent = channel,
                )
                val response = sendMessage(webSocketRequest)
                if (response == null || response.success != true) {
                    Timber.e("Unable to subscribe to $type with data $data")
                    synchronized(activeMessages) {
                        activeMessages.entries
                            .firstOrNull { it.value.message == subscribeMessage }
                            ?.let { activeMessages.remove(it.key) }
                    }
                    return null
                }
            }
        }
        return synchronized(activeMessages) { activeMessages.values.find { it.message == subscribeMessage }?.eventFlow as? Flow<T> }
    }

    override fun shutdown() {
        Timber.i("Shutting down websocket")
        connection?.close(1001, "Session removed from app.")
    }

    // ----- WebSocketListener section
    override fun onOpen(webSocket: WebSocket, response: Response) {
        Timber.d("Websocket: onOpen")
    }

    override fun onMessage(webSocket: WebSocket, text: String) {
        Timber.d("Websocket: onMessage (${if (BuildConfig.DEBUG) "text: $text" else "text"})")
        val textTree = webSocketMapper.readTree(text)
        val messages: List<SocketResponse> = if (textTree.isArray) {
            textTree.elements().asSequence().toList().map { webSocketMapper.convertValue(it) }
        } else {
            listOf(webSocketMapper.readValue(text))
        }

        // Send messages to the queue to ensure they are handled in order and don't block the function
        messages.forEach { message ->
            Timber.d("Message number ${message.id} received")
            val success = messageQueue.trySend(
                ioScope.launch(start = CoroutineStart.LAZY) {
                    when (message.type) {
                        "auth_required" -> Timber.d("Auth Requested")
                        "auth_ok" -> handleAuthComplete(true, message.haVersion)
                        "auth_invalid" -> handleAuthComplete(false, message.haVersion)
                        "pong", "result" -> handleMessage(message)
                        "event" -> handleEvent(message)
                        else -> Timber.d("Unknown message type: ${message.type}")
                    }
                },
            )
            if (!success.isSuccess) Timber.w("Message number ${message.id} not being processed")
        }
    }

    override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
        Timber.d("Websocket: onMessage (bytes)")
    }

    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
        Timber.d("Websocket: onClosing code: $code, reason: $reason")
        handleClosingSocket()
    }

    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
        Timber.d("Websocket: onClosed")
        handleClosingSocket()
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        Timber.e(t, "Websocket: onFailure")
        if (connected.isActive) {
            connected.completeExceptionally(t)
        }
        handleClosingSocket()
    }

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
                if (!it.hasContinuationBeenInvoked.getAndSet(true) && cont.isActive) {
                    cont.resumeWith(Result.success(response))
                } else {
                    Timber.w("Response continuation has already been invoked for ${response.id}, ${response.event}")
                }
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
                    webSocketMapper.convertValue(
                        response.event,
                        object : TypeReference<Map<String, Any>>() {},
                    )
                } else if (subscriptionType == SUBSCRIBE_TYPE_SUBSCRIBE_ENTITIES) {
                    webSocketMapper.convertValue(response.event, CompressedStateChangedEvent::class.java)
                } else if (subscriptionType == SUBSCRIBE_TYPE_RENDER_TEMPLATE) {
                    webSocketMapper.convertValue(response.event, TemplateUpdatedEvent::class.java)
                } else if (subscriptionType == SUBSCRIBE_TYPE_SUBSCRIBE_TRIGGER) {
                    val trigger = response.event?.get("variables")?.get("trigger")
                    if (trigger != null) {
                        webSocketMapper.convertValue(trigger, TriggerEvent::class.java)
                    } else {
                        Timber.w("Received no trigger value for trigger subscription, skipping")
                        return
                    }
                } else if (subscriptionType == SUBSCRIBE_TYPE_ASSIST_PIPELINE_RUN) {
                    val eventType = response.event?.get("type")
                    if (eventType?.isTextual == true) {
                        val eventDataMap = response.event.get("data")
                        val eventData = when (eventType.textValue()) {
                            AssistPipelineEventType.RUN_START -> webSocketMapper.convertValue(eventDataMap, AssistPipelineRunStart::class.java)
                            AssistPipelineEventType.STT_END -> webSocketMapper.convertValue(eventDataMap, AssistPipelineSttEnd::class.java)
                            AssistPipelineEventType.INTENT_START -> webSocketMapper.convertValue(eventDataMap, AssistPipelineIntentStart::class.java)
                            AssistPipelineEventType.INTENT_PROGRESS -> webSocketMapper.convertValue(eventDataMap, AssistPipelineIntentProgress::class.java)
                            AssistPipelineEventType.INTENT_END -> webSocketMapper.convertValue(eventDataMap, AssistPipelineIntentEnd::class.java)
                            AssistPipelineEventType.TTS_END -> webSocketMapper.convertValue(eventDataMap, AssistPipelineTtsEnd::class.java)
                            AssistPipelineEventType.ERROR -> webSocketMapper.convertValue(eventDataMap, AssistPipelineError::class.java)
                            else -> {
                                Timber.d("Unknown event type ignoring. received data = \n$response")
                                null
                            }
                        }
                        AssistPipelineEvent(eventType.textValue(), eventData)
                    } else {
                        Timber.w("Received Assist pipeline event without type, skipping")
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
                            Timber.d("Unknown event type received")
                            object : TypeReference<EventResponse<Any>>() {}
                        }
                    }

                    webSocketMapper.convertValue(
                        response.event,
                        eventResponseClass,
                    ).data
                } else {
                    Timber.d("Unknown event for subscription received, skipping")
                    return
                }

            try {
                messageData.onEvent?.send(message)
            } catch (e: Exception) {
                Timber.e(e, "Unable to send event message to channel")
            }
        } ?: run {
            Timber.d("Received event for unknown subscription, unsubscribing")
            sendMessage(
                mapOf(
                    "type" to "unsubscribe_events",
                    "subscription" to subscriptionId,
                ),
            )
        }
    }

    private fun handleClosingSocket() {
        ioScope.launch {
            connectedMutex.withLock {
                connected = CompletableDeferred()
                connection = null
                connectionHaVersion = null
                if (connectionState != WebSocketState.CLOSED_AUTH) {
                    connectionState = WebSocketState.CLOSED_OTHER
                }
                synchronized(activeMessages) {
                    activeMessages
                        .filterValues { it.eventFlow == null }
                        .forEach {
                            it.value.onResponse?.let { cont ->
                                if (!it.value.hasContinuationBeenInvoked.getAndSet(true) && cont.isActive) {
                                    cont.resumeWithException(IOException())
                                } else {
                                    Timber.w("Response continuation has already been invoked, skipping IOException")
                                }
                            }
                            activeMessages.remove(it.key)
                        }
                }
            }
        }
        // If we still have flows flowing
        val hasFlowMessages = synchronized(activeMessages) { activeMessages.any { it.value.eventFlow != null } }
        if (hasFlowMessages && ioScope.isActive) {
            ioScope.launch {
                delay(10.seconds)
                if (connect()) {
                    Timber.d("Resubscribing to active subscriptions...")
                    synchronized(activeMessages) {
                        activeMessages.filterValues { it.eventFlow != null }.entries
                    }.forEach { (oldId, oldMessage) ->
                        val response = sendMessage(oldMessage)
                        if (response == null || response.success != true) {
                            Timber.e("Issue re-registering subscription with ${oldMessage.message}")
                        } else {
                            // sendMessage will have created a new item for this subscription
                            activeMessages.remove(oldId)
                        }
                    }
                }
            }
        }
    }
}
