package io.homeassistant.companion.android.common.data.websocket.impl

import androidx.annotation.VisibleForTesting
import io.homeassistant.companion.android.common.BuildConfig
import io.homeassistant.companion.android.common.data.HomeAssistantApis.Companion.USER_AGENT
import io.homeassistant.companion.android.common.data.HomeAssistantApis.Companion.USER_AGENT_STRING
import io.homeassistant.companion.android.common.data.HomeAssistantVersion
import io.homeassistant.companion.android.common.data.authentication.AuthorizationException
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.data.websocket.WebSocketCore
import io.homeassistant.companion.android.common.data.websocket.WebSocketRequest
import io.homeassistant.companion.android.common.data.websocket.WebSocketState
import io.homeassistant.companion.android.common.data.websocket.impl.WebSocketConstants.EVENT_AREA_REGISTRY_UPDATED
import io.homeassistant.companion.android.common.data.websocket.impl.WebSocketConstants.EVENT_DEVICE_REGISTRY_UPDATED
import io.homeassistant.companion.android.common.data.websocket.impl.WebSocketConstants.EVENT_ENTITY_REGISTRY_UPDATED
import io.homeassistant.companion.android.common.data.websocket.impl.WebSocketConstants.EVENT_STATE_CHANGED
import io.homeassistant.companion.android.common.data.websocket.impl.WebSocketConstants.SUBSCRIBE_TYPE_ASSIST_PIPELINE_RUN
import io.homeassistant.companion.android.common.data.websocket.impl.WebSocketConstants.SUBSCRIBE_TYPE_RENDER_TEMPLATE
import io.homeassistant.companion.android.common.data.websocket.impl.WebSocketConstants.SUBSCRIBE_TYPE_SUBSCRIBE_ENTITIES
import io.homeassistant.companion.android.common.data.websocket.impl.WebSocketConstants.SUBSCRIBE_TYPE_SUBSCRIBE_TRIGGER
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
import io.homeassistant.companion.android.common.data.websocket.impl.entities.AuthInvalidSocketResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.AuthOkSocketResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.AuthRequiredSocketResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.CompressedStateChangedEvent
import io.homeassistant.companion.android.common.data.websocket.impl.entities.DeviceRegistryUpdatedEvent
import io.homeassistant.companion.android.common.data.websocket.impl.entities.EntityRegistryUpdatedEvent
import io.homeassistant.companion.android.common.data.websocket.impl.entities.EventResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.EventSocketResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.MessageSocketResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.PongSocketResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.RawMessageSocketResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.SocketResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.StateChangedEvent
import io.homeassistant.companion.android.common.data.websocket.impl.entities.TemplateUpdatedEvent
import io.homeassistant.companion.android.common.data.websocket.impl.entities.TriggerEvent
import io.homeassistant.companion.android.common.data.websocket.impl.entities.UnknownTypeSocketResponse
import io.homeassistant.companion.android.common.util.FailFast
import io.homeassistant.companion.android.common.util.MapAnySerializer
import io.homeassistant.companion.android.common.util.kotlinJsonMapper
import java.io.IOException
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.resumeWithException
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import timber.log.Timber

private val DELAY_BEFORE_RECONNECT = 10.seconds

/**
 * Implementation of the [WebSocketCore] interface for managing WebSocket connections to a Home Assistant server.
 *
 * ### Implementation Details:
 *
 * #### Message handling:
 * - All incoming messages are received in [onMessage], parsed, and dispatched to their appropriate handlers based on their types and IDs.
 * - Messages are processed sequentially using the [messageQueue], ensuring that each message is handled in order without race conditions.
 * - The [activeMessages] map is used to track requests and their corresponding IDs. If no matching ID is found or an error occurs, the message is dropped.
 *
 * #### Connection lifecycle:
 * - The WebSocket connection will automatically close itself after the last subscription created with [subscribeTo] is dismissed.
 * - Calling [sendMessage] will open the connection if it is closed. Once the connection is open, it remains open and does not close automatically.
 * - If the connection is lost or closed, the implementation waits for [DELAY_BEFORE_RECONNECT] before attempting to reconnect.
 *
 * #### Reconnection and re-subscription:
 * - On failure or when the socket is closing, if there are active subscriptions created with [subscribeTo], the implementation will automatically retry to open the connection until it succeeds.
 * - Upon reconnection, the implementation resubscribes to all active subscriptions to ensure continuity.
 *
 * #### Supported features:
 * - If the connected server version is 2022.9 or above, the implementation automatically sends a `supported_features` message after the connection is established.
 *
 * #### Threading:
 * - All access to the [connection] is synchronized using the [connectedMutex] to ensure thread safety.
 * - The [eventSubscriptionMutex] ensures that only one subscription is created for multiple invocations with the same parameters.
 * - The implementation uses coroutines extensively for asynchronous operations, ensuring non-blocking behavior.
 *
 * @param okHttpClient The HTTP client used to establish the WebSocket connection.
 * @param serverManager Manages server configurations and authentication.
 * @param serverId The ID of the server to connect to.
 * @param wsScope The coroutine scope used for WebSocket operations. Defaults to a scope with `Dispatchers.IO`.
 * @param backgroundScope A dedicated scope for background tasks, primarily used for testing.
 */
internal class WebSocketCoreImpl(
    private val okHttpClient: OkHttpClient,
    private val serverManager: ServerManager,
    private val serverId: Int,
    private val wsScope: CoroutineScope = CoroutineScope(
        Dispatchers.IO + SupervisorJob() +
            CoroutineExceptionHandler { ctx, err -> Timber.e(err, "Uncaught exception in WebSocketCoreImpl") },
    ),
    // We need a dedicated scope in test to control job that are in background
    private val backgroundScope: CoroutineScope = wsScope,
) : WebSocketListener(),
    WebSocketCore {

    /**
     * Tracks active WebSocket requests by their unique IDs.
     * We never modify the entries in the map, only add or remove.
     */
    @VisibleForTesting
    val activeMessages = ConcurrentHashMap<Long, WebSocketRequest>()

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
     * A new instance is created when the connection closed, so it can be reused to reconnect.
     */
    private var connected = CompletableDeferred<Boolean>()
    private val eventSubscriptionMutex = Mutex()

    private val messageQueue = Channel<Job>(capacity = Channel.UNLIMITED).apply {
        backgroundScope.launch {
            consumeEach { it.join() } // Run a job, and wait for it to complete before starting the next one
        }
    }

    override suspend fun server() = serverManager.getServer(serverId)

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

            try {
                connection = okHttpClient.newWebSocket(
                    Request.Builder().url(url.toWebSocketURL()).header(USER_AGENT, USER_AGENT_STRING).build(),
                    this,
                ).also {
                    // Preemptively send auth
                    connectionState = WebSocketState.AUTHENTICATING
                    val result = it.send(
                        kotlinJsonMapper.encodeToString(
                            mapOf(
                                "type" to "auth",
                                "access_token" to serverManager.authenticationRepository(
                                    serverId,
                                ).retrieveAccessToken(),
                            ),
                        ),
                    )
                    if (!result) {
                        Timber.e("Unable to send auth message")
                        connectionState = null
                        return false
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Unable to connect")
                connectionState = null
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
                            val result = it.send(
                                kotlinJsonMapper.encodeToString(MapAnySerializer, supportedFeaturesMessage),
                            )
                            if (!result) {
                                // Something got wrong when sending the message but we should not change the status of the
                                // connection here. If an error occur in the WS it will be handled in the onFailure.
                                Timber.w("Unable to send supported features message")
                            }
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

    override suspend fun sendMessage(request: Map<String, Any?>): RawMessageSocketResponse? =
        sendMessage(WebSocketRequest(request))

    override suspend fun sendMessage(request: WebSocketRequest): RawMessageSocketResponse? {
        return if (connect()) {
            withTimeoutOrNull(request.timeout) {
                var requestId: Long? = null
                try {
                    suspendCancellableCoroutine { cont ->
                        // Lock on the connection so that we fully send before allowing another send.
                        // This should prevent out of order errors.
                        connection?.let {
                            synchronized(it) {
                                requestId = id.getAndIncrement()
                                val outbound = request.message.plus("id" to requestId)
                                Timber.d("Sending message $requestId: $outbound")
                                activeMessages[requestId] = request.apply {
                                    onResponse = cont
                                }
                                val result = connection?.send(
                                    kotlinJsonMapper.encodeToString(MapAnySerializer, outbound),
                                )
                                if (result == false) {
                                    // Something got wrong when sending the message we won't get any answer let's stop there
                                    cont.resumeWithException(IOException("Error sending message"))
                                } else {
                                    Timber.d("Message number $requestId sent awaiting answer from WebSocket")
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    // It can be either IOException or CancellationException
                    // In any case we remove the message from the activeMessages to not keep it forever since it won't get remove otherwise.
                    Timber.e(e, "Exception while sending message")
                    requestId?.let {
                        activeMessages.remove(it)
                    }
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
            connection?.let {
                synchronized(it) {
                    it.send(data.toByteString())
                }
            }
        } else {
            Timber.w("Unable to send bytes, not connected")
            null
        }
    }

    override suspend fun <T : Any> subscribeTo(
        type: String,
        data: Map<String, Any?>,
        timeout: kotlin.time.Duration,
    ): Flow<T>? {
        val subscribeMessage = mapOf(
            "type" to type,
        ).plus(data)

        return eventSubscriptionMutex.withLock<Flow<T>?> {
            ( // Check for existing subscription before creating a new one
                activeMessages.entries.firstOrNull { it.value.message == subscribeMessage }?.value?.eventFlow
                    ?: createSubscriptionFlow<T>(subscribeMessage, timeout)
                ) as? Flow<T>
        }
    }

    override fun shutdown() {
        Timber.i("Shutting down websocket")
        connection?.close(1001, "Session removed from app.")
    }

    // ----- WebSocketListener section
    override fun onOpen(webSocket: WebSocket, response: Response) {
        Timber.d("Websocket: onOpen")
    }

    @OptIn(DelicateCoroutinesApi::class)
    override fun onMessage(webSocket: WebSocket, text: String) {
        Timber.d("Websocket: onMessage (${if (BuildConfig.DEBUG) "text: $text" else "text"})")
        val jsonElement = kotlinJsonMapper.decodeFromString<JsonElement>(text)
        val messages: List<SocketResponse> = if (jsonElement is JsonArray) {
            jsonElement.map { kotlinJsonMapper.decodeFromJsonElement<SocketResponse>(it) }
        } else {
            listOf(kotlinJsonMapper.decodeFromJsonElement<SocketResponse>(jsonElement))
        }

        // Send messages to the queue to ensure they are handled in order and don't block the function
        messages.forEach { message ->
            Timber.d("Message id ${message.maybeId()} received")
            val result = messageQueue.trySend(
                wsScope.launch(start = CoroutineStart.LAZY) {
                    when (message) {
                        is AuthRequiredSocketResponse -> Timber.d("Auth Requested")
                        is AuthOkSocketResponse, is AuthInvalidSocketResponse -> handleAuthComplete(
                            message is AuthOkSocketResponse,
                            message.haVersion,
                        )

                        is EventSocketResponse -> handleEvent(message)
                        is MessageSocketResponse, is PongSocketResponse -> handleMessage(message)
                        is UnknownTypeSocketResponse -> Timber.w("Unknown message received: $message")
                    }
                },
            )

            FailFast.failWhen(!result.isSuccess) {
                "Failed to process message (ID: ${message.maybeId()}). " +
                    "IsFailure? ${result.isFailure}. " +
                    "Is wsScope active? ${wsScope.isActive}. " +
                    "Queue status: isClosedForSend = ${messageQueue.isClosedForSend}. " +
                    "Exception: ${result.exceptionOrNull()}"
            }
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

    private suspend fun <T> createSubscriptionFlow(
        subscribeMessage: Map<String, Any?>,
        timeout: kotlin.time.Duration,
    ): Flow<T>? {
        val channel = Channel<T>(capacity = Channel.BUFFERED)
        val flow = callbackFlow<T> {
            launch { channel.consumeAsFlow().collect(::send) }
            awaitClose {
                wsScope.launch {
                    var subscription: Long? = null
                    eventSubscriptionMutex.withLock {
                        activeMessages.entries.firstOrNull { it.value.message == subscribeMessage }
                            ?.let {
                                subscription = it.key
                                channel.close()
                                activeMessages.remove(subscription)
                            }
                    }
                    subscription?.let {
                        Timber.d("Unsubscribing from $subscribeMessage")
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
        }.shareIn(backgroundScope, SharingStarted.WhileSubscribed(timeout.inWholeMilliseconds, 0))

        val webSocketRequest = WebSocketRequest(
            message = subscribeMessage,
            eventFlow = flow as SharedFlow<Any>?,
            onEvent = channel as Channel<Any>?,
        )
        val response = sendMessage(webSocketRequest)
        if (response == null || response.success != true) {
            Timber.e("Unable to subscribe to $subscribeMessage")
            activeMessages.entries
                .firstOrNull { it.value.message == subscribeMessage }
                ?.let { activeMessages.remove(it.key) }
            return null
        } else {
            return flow
        }
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

    private fun handleMessage(response: RawMessageSocketResponse) {
        val id = response.id!!
        activeMessages[id]?.let {
            it.onResponse?.let { cont ->
                if (!it.hasContinuationBeenInvoked.getAndSet(true) && cont.isActive) {
                    cont.resumeWith(Result.success(response))
                } else {
                    Timber.w("Response continuation has already been invoked for ${response.id}")
                }
            }
            if (it.eventFlow == null) {
                activeMessages.remove(id)
            }
        } ?: { Timber.w("Response for message not in activeMessage id($id) skipping") }
    }

    private suspend fun handleEvent(response: EventSocketResponse) {
        // TODO https://github.com/home-assistant/android/issues/5271
        val subscriptionId = response.id
        activeMessages[subscriptionId]?.let { messageData ->
            val subscriptionType = messageData.message["type"]
            val eventResponseType = (response.event as? JsonObject)?.get("event_type")

            val message: Any =
                if ((response.event as? JsonObject)?.contains("hass_confirm_id") == true) {
                    kotlinJsonMapper.decodeFromJsonElement<Map<String, Any?>>(MapAnySerializer, response.event)
                } else if (subscriptionType == SUBSCRIBE_TYPE_SUBSCRIBE_ENTITIES) {
                    if (response.event != null) {
                        kotlinJsonMapper.decodeFromJsonElement<CompressedStateChangedEvent>(response.event)
                    } else {
                        Timber.w("Received no event for entity subscription, skipping")
                        return
                    }
                } else if (subscriptionType == SUBSCRIBE_TYPE_RENDER_TEMPLATE) {
                    if (response.event != null) {
                        kotlinJsonMapper.decodeFromJsonElement<TemplateUpdatedEvent>(response.event)
                    } else {
                        Timber.w("Received no event for template subscription, skipping")
                        return
                    }
                } else if (subscriptionType == SUBSCRIBE_TYPE_SUBSCRIBE_TRIGGER) {
                    val trigger = ((response.event as? JsonObject)?.get("variables") as? JsonObject)?.get("trigger")
                    if (trigger != null) {
                        kotlinJsonMapper.decodeFromJsonElement<TriggerEvent>(trigger)
                    } else {
                        Timber.w("Received no trigger value for trigger subscription, skipping")
                        return
                    }
                } else if (subscriptionType == SUBSCRIBE_TYPE_ASSIST_PIPELINE_RUN) {
                    val eventType = (response.event as? JsonObject)?.get("type")
                    if ((eventType as? JsonPrimitive)?.isString == true) {
                        val eventDataMap = response.event["data"]
                        if (eventDataMap == null) {
                            Timber.w("Received Assist pipeline event without data, skipping")
                            return
                        }
                        val eventData = when (eventType.jsonPrimitive.content) {
                            AssistPipelineEventType.RUN_START ->
                                kotlinJsonMapper.decodeFromJsonElement<AssistPipelineRunStart>(eventDataMap)

                            AssistPipelineEventType.STT_END ->
                                kotlinJsonMapper.decodeFromJsonElement<AssistPipelineSttEnd>(eventDataMap)

                            AssistPipelineEventType.INTENT_START ->
                                kotlinJsonMapper.decodeFromJsonElement<AssistPipelineIntentStart>(eventDataMap)

                            AssistPipelineEventType.INTENT_PROGRESS ->
                                kotlinJsonMapper.decodeFromJsonElement<AssistPipelineIntentProgress>(eventDataMap)

                            AssistPipelineEventType.INTENT_END ->
                                kotlinJsonMapper.decodeFromJsonElement<AssistPipelineIntentEnd>(eventDataMap)

                            AssistPipelineEventType.TTS_END ->
                                kotlinJsonMapper.decodeFromJsonElement<AssistPipelineTtsEnd>(eventDataMap)

                            AssistPipelineEventType.ERROR ->
                                kotlinJsonMapper.decodeFromJsonElement<AssistPipelineError>(eventDataMap)

                            else -> {
                                Timber.d("Unknown event type ignoring. received data = \n$response")
                                null
                            }
                        }
                        AssistPipelineEvent(eventType.jsonPrimitive.content, eventData)
                    } else {
                        Timber.w("Received Assist pipeline event without type, skipping")
                        return
                    }
                } else if (eventResponseType != null && (eventResponseType as? JsonPrimitive)?.isString == true) {
                    when (eventResponseType.content) {
                        EVENT_STATE_CHANGED -> {
                            kotlinJsonMapper.decodeFromJsonElement<EventResponse<StateChangedEvent>>(
                                response.event,
                            ).data
                        }

                        EVENT_AREA_REGISTRY_UPDATED -> {
                            kotlinJsonMapper.decodeFromJsonElement<EventResponse<AreaRegistryUpdatedEvent>>(
                                response.event,
                            ).data
                        }

                        EVENT_DEVICE_REGISTRY_UPDATED -> {
                            kotlinJsonMapper.decodeFromJsonElement<EventResponse<DeviceRegistryUpdatedEvent>>(
                                response.event,
                            ).data
                        }

                        EVENT_ENTITY_REGISTRY_UPDATED -> {
                            kotlinJsonMapper.decodeFromJsonElement<EventResponse<EntityRegistryUpdatedEvent>>(
                                response.event,
                            ).data
                        }

                        else -> {
                            Timber.d("Unknown event type received ${response.event}")
                            return
                        }
                    }
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
        val cancelPendingMessagesJob = wsScope.launch {
            connectedMutex.withLock {
                connected = CompletableDeferred()
                connection = null
                connectionHaVersion = null
                if (connectionState != WebSocketState.CLOSED_AUTH) {
                    connectionState = WebSocketState.CLOSED_OTHER
                }
                activeMessages
                    .filterValues { it.eventFlow == null }
                    .forEach {
                        it.value.onResponse?.let { cont ->
                            if (!it.value.hasContinuationBeenInvoked.getAndSet(true) && cont.isActive) {
                                cont.resumeWithException(IOException("Connection closed"))
                            } else {
                                Timber.w("Response continuation has already been invoked, skipping IOException")
                            }
                        }
                        activeMessages.remove(it.key)
                    }
            }
        }
        // If we still have flows flowing
        val hasFlowMessages = activeMessages.any { it.value.eventFlow != null }
        if (hasFlowMessages && wsScope.isActive) {
            wsScope.launch {
                cancelPendingMessagesJob.join()
                delay(DELAY_BEFORE_RECONNECT)
                if (connect()) {
                    Timber.d("Resubscribing to active subscriptions...")
                    activeMessages.filterValues { it.eventFlow != null }.entries
                        .forEach { (oldId, oldMessage) ->
                            activeMessages.remove(oldId)

                            val newMessage = WebSocketRequest(
                                message = oldMessage.message,
                                eventFlow = oldMessage.eventFlow,
                                onEvent = oldMessage.onEvent,
                            )

                            val response = sendMessage(newMessage)
                            if (response == null || response.success != true) {
                                Timber.e("Issue re-registering subscription with ${oldMessage.message}")
                            }
                        }
                } else {
                    // TODO https://github.com/home-assistant/android/issues/5259 handle re-connection gracefully or terminates the flows
                    Timber.w("Unable to reconnect cannot resubscribe to active subscriptions")
                }
            }
        }
    }

    private fun URL.toWebSocketURL(): String {
        return toString()
            .replace("https://", "wss://")
            .replace("http://", "ws://")
            .plus("api/websocket")
    }
}
