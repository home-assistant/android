package io.homeassistant.companion.android.common.data.websocket.impl

import androidx.annotation.GuardedBy
import androidx.annotation.VisibleForTesting
import io.homeassistant.companion.android.common.BuildConfig
import io.homeassistant.companion.android.common.data.HomeAssistantApis.Companion.USER_AGENT
import io.homeassistant.companion.android.common.data.HomeAssistantApis.Companion.USER_AGENT_STRING
import io.homeassistant.companion.android.common.data.HomeAssistantVersion
import io.homeassistant.companion.android.common.data.authentication.AuthorizationException
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.data.servers.UrlState
import io.homeassistant.companion.android.common.data.websocket.HAWebSocketException
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
import java.util.concurrent.atomic.AtomicReference
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
import kotlinx.coroutines.flow.collectLatest
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

    private val connectionHolder = AtomicReference<ConnectionHolder?>(null)

    /**
     * Represents the current state of the WebSocket connection.
     *
     * **Thread safety:** This variable is accessed from both the coroutine thread (in [connect])
     * and the OkHttp callback thread (in [WebSocketListener] methods). However, no synchronization
     * is needed because:
     * - The coroutine only sets this when no WebSocket exists yet (URL is null or creation failed),
     *   meaning no listener callbacks can occur.
     * - Once a WebSocket is created, only the listener callbacks ([onOpen], [handleAuthComplete],
     *   [handleClosingSocket]) modify this state.
     *
     * This ensures there's no concurrent modification - the coroutine and listener never write
     * to this variable at the same time.
     */
    private var connectionState: WebSocketState = WebSocketState.Initial

    /**
     * Used to communicate the close reason to [handleClosingSocket] when closing due to URL change.
     * This allows the close reason to be set before calling [WebSocket.cancel], which triggers [onFailure].
     * It is important to keep the previous state to help in the decision of reconnecting or not.
     */
    @Volatile
    private var pendingCloseReason: WebSocketState.Closed.Reason? = null

    /**
     * A [CompletableDeferred] that signals the establishment and authentication of a WebSocket connection.
     *
     * This deferred value completes with the [HomeAssistantVersion] (if available null is sent otherwise) upon successful authentication.
     * It completes with an [Exception] if [onFailure] is invoked.
     * It completes with an [AuthorizationException] if the authentication fails.
     *
     * A new instance is created when the connection closes, so it can be reused to reconnect.
     *
     * **Thread safety:** Reassignment of this reference is protected by [connectedMutex].
     * Operations on the deferred itself are thread-safe.
     */
    @GuardedBy("connectedMutex")
    private var authCompleted = CompletableDeferred<HomeAssistantVersion?>()

    /**
     * Tracks an in-progress connection attempt. When non-null, subsequent callers to [connect]
     * will await this deferred instead of starting a new connection attempt.
     *
     * **Thread safety:** Access is protected by [connectedMutex].
     */
    @GuardedBy("connectedMutex")
    private var pendingConnectDeferred: CompletableDeferred<Boolean>? = null

    /**
     * Protects connection lifecycle operations (creation, authentication, closure).
     * Ensures only one connection attempt runs at a time and prevents race conditions
     * between multiples [connect] and [handleClosingSocket].
     */
    private val connectedMutex = Mutex()

    /**
     * Protects subscription operations to ensure only one subscription is created
     * for multiple invocations with the same parameters. Also guards unsubscription
     * during flow cancellation.
     */
    private val eventSubscriptionMutex = Mutex()

    /**
     * Queue for processing incoming WebSocket messages sequentially.
     * Each message handler is wrapped in a [Job] and executed one at a time,
     * ensuring messages are processed in order without race conditions.
     *
     * It uses the [backgroundScope] scope to do the send operation.
     */
    private val messageQueue = Channel<Job>(capacity = Channel.UNLIMITED).apply {
        backgroundScope.launch {
            consumeEach { it.join() }
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

    /**
     * Processes the send command by enqueuing it on the connection, it does not directly make the network call.
     *
     * @param command The command to process. Its [Command.sendCompleted] will be completed
     *                to indicate success or failure for sending.
     */
    private fun processSendCommand(command: Command<*>) {
        val currentConnection = connectionHolder.get()?.webSocket
        if (currentConnection == null) {
            command.sendCompleted.completeExceptionally(
                HAWebSocketException("No connection to send the message to"),
            )
            return
        }
        when (command) {
            is Command.WithAnswer -> {
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
                val result = currentConnection.send(command.data.toByteString())
                command.sendCompleted.complete(result)
            }
        }
    }

    override suspend fun server() = serverManager.getServer(serverId)

    private suspend fun connectionStateProvider() = serverManager.connectionStateProvider(serverId)

    /**
     * Establishes a WebSocket connection and authenticates with the server.
     *
     * Multiple concurrent calls will share the same connection attempt - subsequent callers
     * will await the result of the first caller's connection attempt rather than starting
     * a new one.
     *
     * @return `true` if the connection is successful and authenticated, `false` otherwise.
     */
    @VisibleForTesting
    suspend fun connect(): Boolean {
        val connectDeferred: CompletableDeferred<Boolean>

        // Track pending connection state locally within the urlObserverJob scope.
        // This is read across collectLatest invocations to cancel partial connections on URL change.
        var pendingWebSocket: WebSocket? = null
        var urlObserverJob: Job? = null

        connectedMutex.withLock {
            // Already connected?
            if (connectionHolder.get() != null && authCompleted.isCompleted) {
                if (authCompleted.isCancelled) Timber.w("Trying to connect but was cancelled")
                return !authCompleted.isCancelled
            }

            // Connection already in progress? Reuse its deferred
            pendingConnectDeferred?.takeIf { !it.isCompleted }?.let { existing ->
                Timber.d("Connection already in progress, reusing existing deferred and release lock")
                connectDeferred = existing
                return@withLock
            }

            // Start new connection attempt
            connectDeferred = CompletableDeferred()
            pendingConnectDeferred = connectDeferred

            // Cancel any existing URL observer
            connectionHolder.get()?.urlObserverJob?.cancel()

            urlObserverJob = wsScope.launch {
                connectionStateProvider().urlFlow()
                    .collectLatest { urlState ->
                        connectedMutex.withLock {
                            // This lock might wait for the first withLock to finish but it should be fast
                            val url = (urlState as? UrlState.HasUrl)?.url
                            if (!connectDeferred.isCompleted) {
                                handleNewConnectionAttempt(
                                    url = url,
                                    pendingWebSocket = pendingWebSocket,
                                    connectDeferred = connectDeferred,
                                    urlObserverJob = urlObserverJob!!,
                                    setPendingWebSocket = { pendingWebSocket = it },
                                )
                            } else if (url != connectionHolder.get()?.url) {
                                // Already connected but URL changed
                                handleUrlChangeWhileConnected(urlState)
                            }
                        }
                    }
            }.apply {
                invokeOnCompletion {
                    if (!connectDeferred.isCompleted) {
                        connectDeferred.complete(false)
                    }
                }
            }
        }

        return connectDeferred.await()
    }

    /**
     * Handles a new connection attempt when not yet connected.
     * This is called when the URL flow emits before [connectDeferred] is completed.
     */
    private suspend fun handleNewConnectionAttempt(
        url: URL?,
        pendingWebSocket: WebSocket?,
        connectDeferred: CompletableDeferred<Boolean>,
        urlObserverJob: Job,
        setPendingWebSocket: (WebSocket?) -> Unit,
    ) {
        // Clean up any partial connection before using this URL.
        connectionHolder.set(null)
        pendingWebSocket?.cancel()
        setPendingWebSocket(null)
        authCompleted = CompletableDeferred()

        if (url == null) {
            Timber.w("No URL available to open WebSocket connection")
            connectionState = WebSocketState.ClosedOther
            connectDeferred.complete(false)
            urlObserverJob.cancel()
            return
        }

        when (val attemptResult = attemptWebSocketConnection(url)) {
            is ConnectionAttemptResult.Failed -> {
                connectDeferred.complete(false)
                urlObserverJob.cancel()
            }

            is ConnectionAttemptResult.Success -> {
                val webSocket = attemptResult.webSocket
                setPendingWebSocket(webSocket)

                val authSuccess = awaitAuthAndSetupConnectionHolder(
                    webSocket = webSocket,
                    url = url,
                    urlObserverJob = urlObserverJob,
                )
                connectDeferred.complete(authSuccess)
            }
        }
    }

    private fun handleUrlChangeWhileConnected(urlState: UrlState) {
        val currentHolder = connectionHolder.get()
        if (urlState is UrlState.InsecureState) {
            Timber.w("Insecure state, disconnecting immediately.")
        } else {
            Timber.w("URL changed, disconnecting immediately.")
        }

        if (currentHolder == null) {
            connectionState = WebSocketState.ClosedUrlChange
        } else {
            // Set pending reason before cancel() so handleClosingSocket knows to reconnect immediately
            pendingCloseReason = WebSocketState.Closed.Reason.CHANGED_URL
            currentHolder.webSocket.cancel()
        }
    }

    /**
     * Attempts to create a WebSocket connection and send the authentication message.
     *
     * @return The result of the connection attempt
     */
    private suspend fun attemptWebSocketConnection(url: URL): ConnectionAttemptResult {
        var webSocket: WebSocket? = null
        try {
            Timber.d("Creating WebSocket connection")
            webSocket = okHttpClient.newWebSocket(
                Request.Builder().url(url.toWebSocketURL())
                    .header(USER_AGENT, USER_AGENT_STRING)
                    .build(),
                this@WebSocketCoreImpl,
            )

            val accessToken = serverManager.authenticationRepository(serverId).retrieveAccessToken()
            val result = webSocket.send(
                kotlinJsonMapper.encodeToString(
                    mapOf(
                        "type" to "auth",
                        "access_token" to accessToken,
                    ),
                ),
            )
            if (!result) {
                Timber.e("Unable to send auth message")
                webSocket.cancel() // Won't do anything in handleClosingSocket since holder is null
                return ConnectionAttemptResult.Failed(socketCreated = true)
            }
            return ConnectionAttemptResult.Success(webSocket)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Unable to connect")
            // Set state directly if no WebSocket was created (socket creation failed)
            // Otherwise handleClosingSocket will be called via onFailure when cancel() is called
            if (webSocket == null) {
                connectionState = WebSocketState.ClosedOther
            }
            webSocket?.cancel()
            return ConnectionAttemptResult.Failed(socketCreated = webSocket != null)
        }
    }

    /**
     * Waits for authentication to complete and sets up the [ConnectionHolder].
     * Also sends the supported_features message if the server supports it.
     *
     * @return true if authentication succeeded and holder was created, false otherwise
     */
    private suspend fun awaitAuthAndSetupConnectionHolder(
        webSocket: WebSocket,
        url: URL,
        urlObserverJob: Job,
    ): Boolean {
        val result = withTimeoutOrNull(30.seconds) {
            try {
                val haVersion = authCompleted.await()

                // Create the ConnectionHolder now that we have all the information
                connectionHolder.set(
                    ConnectionHolder(
                        webSocket = webSocket,
                        haVersion = haVersion,
                        url = url,
                        urlObserverJob = urlObserverJob,
                    ),
                )

                if (haVersion?.isAtLeast(2022, 9) == true) {
                    sendSupportedFeatures(webSocket)
                }

                true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Unable to authenticate")
                webSocket.cancel() // This is going to set the state to close other
                connectionHolder.set(null)
                false
            }
        }

        if (result == null) {
            // Timeout occurred
            webSocket.cancel() // This is going to set the state to close other
            connectionHolder.set(null)
            return false
        }

        return result
    }

    private fun sendSupportedFeatures(webSocket: WebSocket) {
        val supportedFeaturesMessage = mapOf(
            "type" to "supported_features",
            "id" to id.getAndIncrement(),
            "features" to mapOf(
                "coalesce_messages" to 1,
            ),
        )
        Timber.d("Sending message ${supportedFeaturesMessage["id"]}: $supportedFeaturesMessage")
        val result = webSocket.send(
            kotlinJsonMapper.encodeToString(
                MapAnySerializer,
                supportedFeaturesMessage,
            ),
        )
        if (!result) {
            Timber.w("Unable to send supported features message")
        }
    }

    override fun getConnectionState(): WebSocketState = connectionState

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

        return try {
            command.sendCompleted.await()
        } catch (e: HAWebSocketException) {
            Timber.e(e, "Failed to send bytes")
            false
        }
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
        connectionHolder.get()?.webSocket?.close(1001, "Session removed from app.")
    }

    // ----- WebSocketListener section

    /**
     * Checks if the given webSocket is stale (no longer the current connection).
     * Callbacks from stale connections should be ignored to prevent acting on outdated state.
     *
     * Note: During connection establishment (before connectionHolder is set), callbacks are
     * allowed through since connect() is managing the lifecycle.
     */
    private fun isStaleConnection(webSocket: WebSocket): Boolean {
        val currentWebSocket = connectionHolder.get()?.webSocket ?: return false
        // If no established connection yet, allow callbacks through (connect() handles them)
        return currentWebSocket != webSocket
    }

    override fun onOpen(webSocket: WebSocket, response: Response) {
        Timber.d("Websocket: onOpen")
        if (isStaleConnection(webSocket)) {
            Timber.w("Ignoring onOpen from stale connection")
            return
        }
        connectionState = WebSocketState.Authenticating
    }

    @OptIn(DelicateCoroutinesApi::class)
    override fun onMessage(webSocket: WebSocket, text: String) {
        Timber.d("Websocket: onMessage (${if (BuildConfig.DEBUG) "text: $text" else "text"})")
        if (isStaleConnection(webSocket)) {
            Timber.w("Ignoring onMessage from stale connection")
            return
        }
        val jsonElement = kotlinJsonMapper.decodeFromString<JsonElement>(text)
        val messages: List<SocketResponse> = if (jsonElement is JsonArray) {
            jsonElement.map { kotlinJsonMapper.decodeFromJsonElement<SocketResponse>(it) }
        } else {
            listOf(kotlinJsonMapper.decodeFromJsonElement<SocketResponse>(jsonElement))
        }

        messages.forEach { message ->
            Timber.d("Message id ${message.maybeId()} received")

            // Handle auth messages directly on this thread to ensure connectionState is set synchronously
            when (message) {
                is AuthRequiredSocketResponse -> {
                    Timber.d("Auth Requested")
                    return@forEach
                }

                is AuthOkSocketResponse, is AuthInvalidSocketResponse -> {
                    handleAuthComplete(message is AuthOkSocketResponse, message.haVersion)
                    return@forEach
                }

                else -> Unit // handle in the queue
            }

            // Send other messages to the queue to ensure they are handled in order
            val result = messageQueue.trySend(
                wsScope.launch(start = CoroutineStart.LAZY) {
                    when (message) {
                        is EventSocketResponse -> handleEvent(message)
                        is MessageSocketResponse, is PongSocketResponse -> handleMessage(message)
                        is UnknownTypeSocketResponse -> Timber.w("Unknown message received: $message")
                        // Auth messages already handled above
                        is AuthRequiredSocketResponse, is AuthOkSocketResponse, is AuthInvalidSocketResponse -> Unit
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
        if (isStaleConnection(webSocket)) {
            Timber.w("Ignoring onClosing from stale connection")
            return
        }
        handleClosingSocket()
    }

    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
        Timber.d("Websocket: onClosed")
        if (isStaleConnection(webSocket)) {
            Timber.w("Ignoring onClosed from stale connection")
            return
        }
        handleClosingSocket()
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        Timber.e(t, "Websocket: onFailure")
        if (isStaleConnection(webSocket)) {
            Timber.w("Ignoring onFailure from stale connection")
            return
        }
        if (authCompleted.isActive) {
            authCompleted.completeExceptionally(t)
        }
        handleClosingSocket()
    }

    private suspend fun <T> createSubscriptionFlow(subscribeMessage: Map<String, Any?>, timeout: Duration): Flow<T>? {
        val channel = Channel<T>(capacity = Channel.BUFFERED)
        val flow = callbackFlow<T> {
            launch { channel.consumeAsFlow().collect(::send) }
            awaitClose {
                wsScope.launch {
                    eventSubscriptionMutex.withLock {
                        findSubscription(subscribeMessage)
                            ?.let {
                                val subscription = it.key
                                Timber.d("Unsubscribing from $subscribeMessage")
                                // Unsubscribe must happen before removing from activeMessages to ensure
                                // the server acknowledges before we stop handling events for this subscription
                                unsubscribeEvents(subscription)
                                channel.close()
                                activeMessages.remove(subscription)
                            }
                    }
                    if (activeMessages.isEmpty()) {
                        Timber.i("No more subscriptions, closing connection.")
                        connectionHolder.get()?.webSocket?.close(1001, "Done listening to subscriptions.")
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
        val parsedVersion = haVersion?.let { HomeAssistantVersion.fromString(it) }
        if (successful) {
            connectionState = WebSocketState.Active
            authCompleted.complete(parsedVersion)
        } else {
            connectionState = WebSocketState.ClosedAuth
            pendingCloseReason = WebSocketState.Closed.Reason.AUTH
            authCompleted.completeExceptionally(AuthorizationException())
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
            FailFast.failWhen(it != null && it !is ActiveMessage.Subscription) {
                // Null is acceptable because a message could still arrive after unsubscribe like run-end in Assist pipeline
                "Event should always be associated to a ActiveMessage.Subscription message"
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
        val previousState = connectionState
        val closeReason = pendingCloseReason ?: WebSocketState.Closed.Reason.OTHER
        val wasActive = previousState == WebSocketState.Active
        pendingCloseReason = null

        // Transition to closed state unless already closed
        when (previousState) {
            is WebSocketState.Closed -> {
                // Already closed, preserve existing reason
            }

            WebSocketState.Authenticating,
            WebSocketState.Active, WebSocketState.Initial,
            -> {
                connectionState = WebSocketState.Closed(closeReason)
            }
        }

        // If null, either connect() is managing lifecycle or already cleaned up
        if (connectionHolder.get() == null) return

        wsScope.launch {
            val hasSubscriptions: Boolean

            connectedMutex.withLock {
                val holder = connectionHolder.getAndSet(null)
                // Cancel URL observer - connect() will recreate it if needed
                holder?.urlObserverJob?.cancel()

                // Complete the connected deferred if still active
                if (authCompleted.isActive) {
                    authCompleted.completeExceptionally(IOException("Connection closed"))
                }
                authCompleted = CompletableDeferred()

                // Complete all pending simple messages with error
                activeMessages
                    .filterValues { it is ActiveMessage.Simple }
                    .forEach { (key, activeMessage) ->
                        val completed =
                            activeMessage.responseDeferred.completeExceptionally(IOException("Connection closed"))
                        if (!completed) {
                            Timber.w("Response deferred was already completed, skipping IOException")
                        }
                        activeMessages.remove(key)
                    }
                hasSubscriptions = activeMessages.any { it.value is ActiveMessage.Subscription }
            }

            val shouldAttemptReconnect = hasSubscriptions && wasActive

            if (shouldAttemptReconnect && wsScope.isActive) {
                // Delay before reconnect unless URL changed
                if (closeReason != WebSocketState.Closed.Reason.CHANGED_URL) {
                    delay(DELAY_BEFORE_RECONNECT)
                }
                reconnectSubscriptions()
            }
        }
    }

    private suspend fun reconnectSubscriptions() {
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
            Timber.w("Unable to reconnect, cannot resubscribe to active subscriptions")
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
 * Holds connection-related state for a fully established connection.
 */
private data class ConnectionHolder(
    val webSocket: WebSocket,
    val haVersion: HomeAssistantVersion?,
    val url: URL,
    val urlObserverJob: Job,
)

/**
 * Result of attempting to create a WebSocket connection.
 */
private sealed interface ConnectionAttemptResult {
    /** WebSocket created and auth message sent successfully */
    data class Success(val webSocket: WebSocket) : ConnectionAttemptResult

    /** Failed to create WebSocket or send auth */
    data class Failed(val socketCreated: Boolean) : ConnectionAttemptResult
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
