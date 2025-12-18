package io.homeassistant.companion.android.common.data.websocket.impl

import androidx.annotation.VisibleForTesting
import io.homeassistant.companion.android.common.BuildConfig
import io.homeassistant.companion.android.common.data.HomeAssistantApis.Companion.USER_AGENT
import io.homeassistant.companion.android.common.data.HomeAssistantApis.Companion.USER_AGENT_STRING
import io.homeassistant.companion.android.common.data.HomeAssistantVersion
import io.homeassistant.companion.android.common.data.authentication.AuthorizationException
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.data.servers.UrlState
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
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
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
 * - Connection lifecycle (creation, authentication, closure) is protected by [connectedMutex].
 * - Outbound messages are serialized through a dedicated channel to ensure ordering.
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
    val activeMessages = ConcurrentHashMap<Long, ActiveMessage>()

    // Each message that we send needs a unique ID to match it to the answer
    private val id = AtomicLong(1)
    private var connection: WebSocket? = null
    private var connectionState: WebSocketState? = null
    private var connectionHaVersion: HomeAssistantVersion? = null
    private var connectedUrl: URL? = null
    private var urlObserverJob: Job? = null
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

    /**
     * Channel for send commands one by one in sequential order. It could have been
     * a coroutine Actor but the Actor API is obsolete.
     *
     * This is to ensure that the order of sending command is respected.
     *
     * It uses the [backgroundScope] scope to do the send operation.
     */
    private val sendCommandChannel = Channel<Command<*>>(capacity = Channel.UNLIMITED).apply {
        backgroundScope.launch {
            consumeEach {
                processSendCommand(it)
            }
        }
    }

    // TODO check the base type and if we want to keep this public
    class HAWebSocketException(override val message: String?) : Exception()

    /**
     * This complete when the message has been processed and sent or not.
     * This is not making any network call directly it enqueue the send into the WebSocket connection.
     */
    private fun processSendCommand(command: Command<*>) {
        // TODO do we need any protection on the connection like in connect() with the Mutex
        val currentConnection = connection
        when (command) {
            is Command.WithAnswer -> {
                if (currentConnection == null) {
                    command.sendCompleted.completeExceptionally(
                        HAWebSocketException("No connection to send the message to"),
                    )
                    return
                }
                val id = id.getAndIncrement()
                val outbound = command.request.message.plus("id" to id)
                Timber.d("Sending message $id: $outbound")
                activeMessages[id] = command.toActiveMessage()
                val result = currentConnection.send(
                    kotlinJsonMapper.encodeToString(MapAnySerializer, outbound),
                )
                if (!result) {
                    Timber.e("Failed to send message $id")
                    activeMessages.remove(id)
                    command.sendCompleted.completeExceptionally(HAWebSocketException("Failed to send message"))
                } else {
                    command.sendCompleted.complete(id)
                }
            }

            is Command.Bytes -> {
                val result = currentConnection?.send(command.data.toByteString()) ?: false
                command.sendCompleted.complete(result)
            }
        }
    }

    override suspend fun server() = serverManager.getServer(serverId)

    private suspend fun connectionStateProvider() = serverManager.connectionStateProvider(serverId)

    override suspend fun connect(): Boolean {
        connectedMutex.withLock {
            if (connection != null && connected.isCompleted) {
                return !connected.isCancelled
            }

            val url = startUrlObserverAndAwaitFirstUrl()
            if (url == null) {
                Timber.w("No URL available to open WebSocket connection")
                return false
            }

            try {
                connection = okHttpClient.newWebSocket(
                    Request.Builder().url(url.toWebSocketURL()).header(USER_AGENT, USER_AGENT_STRING).build(),
                    this,
                ).also {
                    // Preemptively send auth
                    connectionState = WebSocketState.AUTHENTICATING
                    connectedUrl = url
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
                        connectedUrl = null
                        urlObserverJob?.cancel()
                        return false
                    }
                }
            } catch (e: CancellationException) {
                connectionState = null
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Unable to connect")
                connectionState = null
                urlObserverJob?.cancel()
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
                    if (!didConnect) {
                        urlObserverJob?.cancel()
                    }
                    didConnect
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.e(e, "Unable to authenticate")
                    urlObserverJob?.cancel()
                    false
                }
            }
        }
    }

    /**
     * Starts observing URL changes using a single flow subscription.
     * Returns the first available URL and continues observing for changes in the background.
     * When the URL changes, disconnects immediately the WebSocket to trigger reconnection via [handleClosingSocket].
     *
     * @return the first URL if available, or `null` if not
     */
    private suspend fun startUrlObserverAndAwaitFirstUrl(): URL? {
        urlObserverJob?.cancel()

        val firstUrlDeferred = CompletableDeferred<URL?>()

        urlObserverJob = wsScope.launch {
            var isFirstEmission = true
            connectionStateProvider().urlFlow()
                .collect { urlState ->
                    val url = (urlState as? UrlState.HasUrl)?.url
                    if (isFirstEmission) {
                        isFirstEmission = false
                        firstUrlDeferred.complete(url)
                    } else if (url != connectedUrl) {
                        if (urlState is UrlState.InsecureState) {
                            Timber.w("Insecure state, disconnecting immediately.")
                        } else {
                            Timber.w("URL changed, disconnecting immediately.")
                        }
                        // Set state before cancel() since cancel triggers onFailure -> handleClosingSocket
                        connectionState = WebSocketState.CLOSED_URL_CHANGE
                        connection?.cancel()
                    }
                }
        }

        return firstUrlDeferred.await()
    }

    override fun getConnectionState(): WebSocketState? = connectionState

    override suspend fun sendMessage(request: Map<String, Any?>): RawMessageSocketResponse? =
        sendMessage(WebSocketRequest(request))

    override suspend fun sendMessage(request: WebSocketRequest): RawMessageSocketResponse? {
        return sendMessage(
            Command.WithAnswer.Message(request),
        )
    }

    private suspend fun sendMessage(command: Command.WithAnswer): RawMessageSocketResponse? {
        if (!connect()) {
            Timber.w("Unable to send message, not connected: ${command.request}")
            return null
        }

        sendCommandChannel.send(command)

        val id: Long
        try {
            id = command.sendCompleted.await()
        } catch (e: HAWebSocketException) {
            Timber.e(e, "Failed to send message ${command.request}")
            return null
        }

        Timber.d("Message number $id sent, awaiting response from WebSocket")

        // Wait for response with timeout
        val response = try {
            withTimeoutOrNull(command.request.timeout) {
                command.responseDeferred.await()
            }
        } catch (e: CancellationException) {
            activeMessages.remove(id)
            throw e
        } catch (e: Exception) {
            // Connection closed or other error while waiting for response
            Timber.e(e, "Error waiting for response to message $id")
            activeMessages.remove(id)
            return null
        }

        if (response == null) {
            activeMessages.remove(id)
        }

        return response
    }

    override suspend fun sendBytes(data: ByteArray): Boolean {
        if (!connect()) {
            Timber.w("Unable to send bytes, not connected")
            return false
        }

        val command = Command.Bytes(data)

        sendCommandChannel.send(command)

        return command.sendCompleted.await()
    }

    override suspend fun <T : Any> subscribeTo(
        type: String,
        data: Map<String, Any?>,
        timeout: kotlin.time.Duration,
    ): Flow<T>? {
        val subscribeMessage = buildMap {
            put("type", type)
            putAll(data)
        }

        return eventSubscriptionMutex.withLock<Flow<T>?> {
            ( // Check for existing subscription before creating a new one
                (findSubscription(subscribeMessage)?.value as? ActiveMessage.Subscription)?.eventFlow
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

    private suspend fun <T> createSubscriptionFlow(subscribeMessage: Map<String, Any?>, timeout: Duration): Flow<T>? {
        val channel = Channel<T>(capacity = Channel.BUFFERED)
        val flow = callbackFlow<T> {
            launch { channel.consumeAsFlow().collect(::send) }
            awaitClose {
                wsScope.launch {
                    var subscription: Long? = null
                    eventSubscriptionMutex.withLock {
                        findSubscription(subscribeMessage)
                            ?.let {
                                subscription = it.key
                                channel.close()
                                activeMessages.remove(subscription)
                            }
                    }
                    subscription?.let {
                        Timber.d("Unsubscribing from $subscribeMessage")
                        unsubscribeEvents(it)
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

        val response = sendMessage(
            Command.WithAnswer.Subscription(
                request = WebSocketRequest(message = subscribeMessage),
                eventFlow = flow as SharedFlow<Any>,
                onEvent = channel as Channel<Any>,
            ),
        )
        if (response == null || response.success != true) {
            Timber.e("Unable to subscribe to $subscribeMessage")
            findSubscription(subscribeMessage)?.let { activeMessages.remove(it.key) }
            return null
        } else {
            return flow
        }
    }

    private fun findSubscription(subscribeMessage: Map<String, Any?>): Map.Entry<Long, ActiveMessage>? {
        return activeMessages.entries.firstOrNull {
            (it.value as? ActiveMessage.Subscription)?.request?.message ==
                subscribeMessage
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
        val id = checkNotNull(response.id) { "Response without ID" }
        activeMessages[id]?.let { request ->
            // Complete the deferred - this is safe to call multiple times (idempotent)
            val completed = request.responseDeferred.complete(response)
            if (!completed) {
                Timber.w("Response deferred was already completed for ${response.id}")
            }
            if (request !is ActiveMessage.Subscription) {
                activeMessages.remove(id)
            }
        } ?: run { Timber.w("Response for message not in activeMessage id($id) skipping") }
    }

    private suspend fun handleEvent(response: EventSocketResponse) {
        // TODO https://github.com/home-assistant/android/issues/5271
        val subscriptionId = response.id
        val activeMessage = activeMessages[subscriptionId].let {
            FailFast.failWhen(it !is ActiveMessage.Subscription) {
                "Event should always be associated to a ActiveMessage.SubscriptionActiveMessage"
            }
            it as? ActiveMessage.Subscription
        }

        if (activeMessage == null) {
            Timber.d("Received event for unknown subscription id= $subscriptionId, unsubscribing")
            subscriptionId?.let {
                unsubscribeEvents(subscriptionId)
            }
            return
        }
        val subscriptionType = activeMessage.request.message["type"]
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
            activeMessage.onEvent.send(message)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Unable to send event message to channel")
        }
    }

    private suspend fun unsubscribeEvents(id: Long) {
        sendMessage(
            mapOf(
                "type" to "unsubscribe_events",
                "subscription" to id,
            ),
        )
    }

    private fun handleClosingSocket() {
        urlObserverJob?.cancel()
        urlObserverJob = null
        // Capture current state before it gets modified in the mutex block
        val closingState = connectionState
        val cancelPendingMessagesJob = wsScope.launch {
            connectedMutex.withLock {
                connected = CompletableDeferred()
                connection = null
                connectionHaVersion = null
                connectedUrl = null
                // Preserve specific closure states, otherwise set to CLOSED_OTHER
                if (connectionState !in listOf(
                        WebSocketState.CLOSED_AUTH,
                        WebSocketState.CLOSED_URL_CHANGE,
                    )
                ) {
                    connectionState = WebSocketState.CLOSED_OTHER
                }
                activeMessages
                    .filterValues { it is ActiveMessage.Simple }
                    .forEach { (key, activeMessage) ->
                        // Complete exceptionally - this is safe to call multiple times (idempotent)
                        val completed =
                            activeMessage.responseDeferred.completeExceptionally(IOException("Connection closed"))
                        if (!completed) {
                            Timber.w("Response deferred was already completed, skipping IOException")
                        }
                        activeMessages.remove(key)
                    }
            }
        }
        // If we still have flows flowing
        val hasFlowMessages = activeMessages.any { it.value is ActiveMessage.Subscription }
        if (hasFlowMessages && wsScope.isActive) {
            wsScope.launch {
                cancelPendingMessagesJob.join()
                // Try to reconnect immediately on URL change, otherwise use standard delay
                if (closingState != WebSocketState.CLOSED_URL_CHANGE) {
                    delay(DELAY_BEFORE_RECONNECT)
                }
                if (connect()) {
                    Timber.d("Resubscribing to active subscriptions...")
                    activeMessages.filterValues { it is ActiveMessage.Subscription }.entries
                        .forEach { (oldId, oldActiveMessage) ->
                            oldActiveMessage as ActiveMessage.Subscription
                            activeMessages.remove(oldId)

                            val response = sendMessage(
                                Command.WithAnswer.Subscription(
                                    request = oldActiveMessage.request,
                                    eventFlow = oldActiveMessage.eventFlow,
                                    onEvent = oldActiveMessage.onEvent,
                                ),
                            )
                            if (response == null || response.success != true) {
                                Timber.e("Issue re-registering subscription with ${oldActiveMessage.request}")
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

/**
 * Represents an active WebSocket message that has been sent and is awaiting a response.
 *
 * Stored in the active messages map to correlate incoming responses with their original requests.
 */
@VisibleForTesting
internal sealed interface ActiveMessage {
    /** Completes when the server sends a response for this message */
    val responseDeferred: CompletableDeferred<RawMessageSocketResponse>

    /**
     * A one-shot message.
     */
    class Simple(override val responseDeferred: CompletableDeferred<RawMessageSocketResponse>) : ActiveMessage

    /**
     * A subscription that receive multiple events over time.
     *
     * @param responseDeferred Completes with the initial subscription confirmation response
     * @param eventFlow The shared flow that emits events to external subscribers
     * @param onEvent The channel used to send events into the flow
     * @param request The original request, retained for resubscription after reconnection
     */
    class Subscription(
        override val responseDeferred: CompletableDeferred<RawMessageSocketResponse>,
        val eventFlow: SharedFlow<Any>,
        val onEvent: Channel<Any>,
        val request: WebSocketRequest,
    ) : ActiveMessage
}

/**
 * Represents a command to be sent through the send command channel for serialized WebSocket sending.
 *
 * @param T The type of result returned when the send operation completes
 */
private sealed interface Command<T> {
    /** Completes when the send operation finishes, with the result of type [T] */
    val sendCompleted: CompletableDeferred<T>

    /**
     * A command that sends a message and expects a response from the server.
     *
     * @param request The request containing the message to send and timeout configuration
     * @property sendCompleted Completes with the assigned message ID when sent successfully,
     *                         or completes exceptionally with [HAWebSocketException] on failure
     * @property responseDeferred Completes when the server responds to this message
     */
    sealed class WithAnswer(val request: WebSocketRequest) : Command<Long> {
        override val sendCompleted: CompletableDeferred<Long> = CompletableDeferred()
        val responseDeferred: CompletableDeferred<RawMessageSocketResponse> = CompletableDeferred()

        /** Creates an [ActiveMessage] to track this request while awaiting a response */
        abstract fun toActiveMessage(): ActiveMessage

        /** A simple one-shot message that expects a single response */
        class Message(request: WebSocketRequest) : WithAnswer(request) {
            override fun toActiveMessage(): ActiveMessage {
                return ActiveMessage.Simple(responseDeferred)
            }
        }

        /**
         * A subscription message that receives multiple events over time.
         *
         * @param eventFlow The shared flow that emits events to subscribers
         * @param onEvent The channel used internally to send events to the flow
         */
        class Subscription(request: WebSocketRequest, val eventFlow: SharedFlow<Any>, val onEvent: Channel<Any>) :
            WithAnswer(request) {

            override fun toActiveMessage(): ActiveMessage {
                return ActiveMessage.Subscription(
                    responseDeferred,
                    eventFlow,
                    onEvent,
                    request,
                )
            }
        }
    }

    /**
     * A command that sends raw bytes without expecting a typed response.
     * Used for binary data like audio streams.
     *
     * @param data The raw bytes to send
     * @property sendCompleted Completes with `true` if sent successfully, `false` otherwise
     */
    class Bytes(val data: ByteArray) : Command<Boolean> {
        override val sendCompleted: CompletableDeferred<Boolean> = CompletableDeferred()
    }
}
