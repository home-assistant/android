package io.homeassistant.companion.android.common.data.websocket.impl

import app.cash.turbine.test
import app.cash.turbine.turbineScope
import io.homeassistant.companion.android.common.data.authentication.AuthenticationRepository
import io.homeassistant.companion.android.common.data.servers.ServerConnectionStateProvider
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.data.servers.UrlState
import io.homeassistant.companion.android.common.data.websocket.WebSocketRequest
import io.homeassistant.companion.android.common.data.websocket.WebSocketState
import io.homeassistant.companion.android.common.data.websocket.impl.WebSocketConstants.SUBSCRIBE_TYPE_SUBSCRIBE_EVENTS
import io.homeassistant.companion.android.common.data.websocket.impl.entities.MessageSocketResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.StateChangedEvent
import io.homeassistant.companion.android.common.util.DefaultFailFastHandler
import io.homeassistant.companion.android.common.util.FailFast
import io.homeassistant.companion.android.common.util.MapAnySerializer
import io.homeassistant.companion.android.common.util.kotlinJsonMapper
import io.homeassistant.companion.android.database.server.Server
import io.homeassistant.companion.android.database.server.ServerConnectionInfo
import io.homeassistant.companion.android.database.server.ServerSessionInfo
import io.homeassistant.companion.android.database.server.ServerUserInfo
import io.homeassistant.companion.android.testing.unit.ConsoleLogExtension
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import io.mockk.verifyOrder
import java.io.IOException
import kotlin.math.sin
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.api.assertNull
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.fail
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(ConsoleLogExtension::class)
class WebSocketCoreImplTest {
    private lateinit var mockOkHttpClient: OkHttpClient
    private lateinit var mockConnection: WebSocket
    private lateinit var webSocketCore: WebSocketCoreImpl
    private lateinit var webSocketListener: WebSocketListener

    /**
     * Computes a deterministic delay based on the index using a sine function.
     * Returns a delay that is always positive but non linear for concurrent test scenarios.
     * Values are between 5 and 25.
     */
    private fun deterministicDelay(index: Long): Long = ((sin(index.toDouble()) + 1.5) * 10).toLong()

    @BeforeEach
    fun setup() {
        FailFast.setHandler(DefaultFailFastHandler)
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    private fun TestScope.setupServer(
        url: String = "https://io.ha",
        backgroundScope: CoroutineScope = this.backgroundScope,
        urlFlow: Flow<UrlState>? = null,
    ) {
        mockOkHttpClient = mockk<OkHttpClient>(relaxed = true)
        val mockServerManager = mockk<ServerManager>(relaxed = true)
        val mockAuthenticationRepository = mockk<AuthenticationRepository>(relaxed = true)
        val mockConnectionStateProvider = mockk<ServerConnectionStateProvider>(relaxed = true)

        val testServerId = 1
        val testServer = Server(
            id = testServerId,
            _name = "Test Server",
            connection = ServerConnectionInfo(
                externalUrl = url,
            ),
            session = ServerSessionInfo(),
            user = ServerUserInfo(),
        )

        mockConnection = mockk(relaxed = true)
        coEvery { mockServerManager.getServer(any<Int>()) } returns testServer
        coEvery { mockServerManager.authenticationRepository(testServerId) } returns mockAuthenticationRepository
        coEvery { mockServerManager.connectionStateProvider(testServerId) } returns mockConnectionStateProvider
        coEvery { mockAuthenticationRepository.retrieveAccessToken() } returns "mock_access_token"
        // Use OkHttp's URL parsing to normalize URLs (adds trailing slash) like the real implementation
        val parsedUrl = url.takeIf { it.startsWith("http") }?.toHttpUrlOrNull()?.toUrl()
        every { mockConnectionStateProvider.urlFlow() } returns (urlFlow ?: flowOf(UrlState.HasUrl(parsedUrl)))
        // The implementation use a background scope to properly handle async messages, to not block the test
        // we are injecting a background scope to properly control it within the tests, the scope will close itself at the end of the test
        webSocketCore = WebSocketCoreImpl(
            mockOkHttpClient,
            mockServerManager,
            testServerId,
            this,
            backgroundScope,
        )
        webSocketListener = webSocketCore
        every {
            mockOkHttpClient.newWebSocket(
                any(),
                webSocketListener,
            )
        } returns mockConnection
    }

    private fun prepareAuthenticationAnswer(
        haVersion: String = "2025.4.1",
        successfulAuth: Boolean = true,
    ) {
        // Simulate that the server receive the auth message and answer that it is accepted
        every {
            mockConnection.send(
                match<String> { message ->
                    message.contains(""""type":"auth"""") &&
                        message.contains(""""access_token":"mock_access_token"""")
                },
            )
        } answers {
            // Trigger onOpen to set state to Authenticating (in real OkHttp this happens async)
            webSocketListener.onOpen(mockConnection, mockk(relaxed = true))
            assertEquals(WebSocketState.Authenticating, webSocketCore.getConnectionState())
            webSocketListener.onMessage(
                mockConnection,
                """{"type":"${if (successfulAuth) "auth_ok" else "auth_invalid"}","ha_version":"$haVersion"}""",
            )
            true
        }
    }

    private fun mockResultSuccessForId(id: Long) {
        every { mockConnection.send(match<String> { it.contains(""""id":$id""") }) } answers {
            webSocketListener.onMessage(
                mockConnection,
                """{"id":$id,"type":"result","success":true,"result":{}}""",
            )
            true
        }
    }

    private fun closeConnection() {
        webSocketListener.onClosed(mockConnection, 1000, "test")
    }

    private fun mockCancelTriggersOnFailure() {
        every { mockConnection.cancel() } answers {
            webSocketListener.onFailure(mockConnection, IOException("Canceled"), null)
        }
    }

    /*
connect()
     */

    @ParameterizedTest
    @ValueSource(strings = ["", "htt://io.ha", "ws://io.ha", "wss://io.ha"])
    fun `Given invalid url When connect is invoked Then it returns false and connection state is ClosedOther`(
        url: String,
    ) = runTest {
        setupServer(url)

        val result = webSocketCore.connect()

        assertFalse(result)
        assertEquals(WebSocketState.ClosedOther, webSocketCore.getConnectionState())
    }

    @Test
    fun `Given no URL When connect is invoked Then it returns false and connection state is ClosedOther`() = runTest {
        val serverManager = mockk<ServerManager>(relaxed = true)
        val mockConnectionStateProvider = mockk<ServerConnectionStateProvider>(relaxed = true)
        coEvery { serverManager.connectionStateProvider(1) } returns mockConnectionStateProvider
        coEvery { mockConnectionStateProvider.urlFlow() } returns flowOf(UrlState.HasUrl(null))

        val webSocketCore = WebSocketCoreImpl(
            okHttpClient = mockk(),
            serverManager = serverManager,
            serverId = 1,
        )

        val result = webSocketCore.connect()

        assertFalse(result)
        assertEquals(WebSocketState.ClosedOther, webSocketCore.getConnectionState())
    }

    @Test
    fun `Given InsecureState When connect is invoked Then it returns false and connection state is ClosedOther`() = runTest {
        val serverManager = mockk<ServerManager>(relaxed = true)
        val mockConnectionStateProvider = mockk<ServerConnectionStateProvider>(relaxed = true)
        coEvery { serverManager.connectionStateProvider(1) } returns mockConnectionStateProvider
        coEvery { mockConnectionStateProvider.urlFlow() } returns flowOf(UrlState.InsecureState)

        val webSocketCore = WebSocketCoreImpl(
            okHttpClient = mockk(),
            serverManager = serverManager,
            serverId = 1,
        )

        val result = webSocketCore.connect()

        assertFalse(result)
        assertEquals(WebSocketState.ClosedOther, webSocketCore.getConnectionState())
    }

    @Test
    fun `Given failure to send auth message after socket creation When connect is invoked Then it returns false and connection state is ClosedOther`() = runTest {
        setupServer()
        every { mockConnection.send(any<String>()) } returns false
        // When cancel is called after send fails, trigger onFailure to clean up state
        mockCancelTriggersOnFailure()

        val result = webSocketCore.connect()

        assertFalse(result)
        assertEquals(WebSocketState.ClosedOther, webSocketCore.getConnectionState())
    }

    @Test
    fun `Given failure at socket creation When connect is invoked Then it returns false and connection state is ClosedOther`() = runTest {
        setupServer()
        // Simulate a failure while creating the socket
        every { mockOkHttpClient.newWebSocket(any(), any()) } throws IllegalStateException()

        val result = webSocketCore.connect()

        assertFalse(result)
        assertEquals(WebSocketState.ClosedOther, webSocketCore.getConnectionState())
    }

    @ParameterizedTest
    @ValueSource(strings = ["http://io.ha", "https://io.ha", "http://192.168.0.42:8123", "https://192.168.0.42:8123"])
    fun `Given valid url When connect is invoked Then it returns true and connection state is Active`(
        url: String,
    ) = runTest {
        setupServer(url)
        prepareAuthenticationAnswer()

        val result = webSocketCore.connect()
        assertTrue(result)
        assertEquals(WebSocketState.Active, webSocketCore.getConnectionState())
    }

    @Test
    fun `Given invalid authentication When connect is invoked Then it returns false and connection state is CLOSED_AUTH`() = runTest {
        setupServer()
        prepareAuthenticationAnswer(successfulAuth = false)

        val result = webSocketCore.connect()

        assertFalse(result)
        assertEquals(WebSocketState.ClosedAuth, webSocketCore.getConnectionState())
    }

    @Test
    fun `Given a valid configuration for 2023 1 1 server When connect is invoked Then it returns true, sends the supported_features message, and connection state is Active`() = runTest {
        setupServer()
        prepareAuthenticationAnswer("2023.1.1")

        val result = webSocketCore.connect()
        assertTrue(result)

        coVerify {
            mockConnection.send(
                kotlinJsonMapper.encodeToString<Map<String, Any>>(
                    MapAnySerializer,
                    mapOf(
                        "type" to "supported_features",
                        // Should be the first message
                        "id" to 1,
                        "features" to mapOf(
                            "coalesce_messages" to 1,
                        ),
                    ),
                ),
            )
        }
        // auth and supported_features
        coVerify(exactly = 2) { mockConnection.send(any<String>()) }
        assertEquals(WebSocketState.Active, webSocketCore.getConnectionState())
    }

    @Test
    fun `Given a valid configuration for 2020 1 1 server When connect is invoked Then it returns true and connection state is Active`() = runTest {
        setupServer()
        prepareAuthenticationAnswer("2020.1.1")

        val result = webSocketCore.connect()

        assertTrue(result)
        // auth
        coVerify(exactly = 1) { mockConnection.send(any<String>()) }
        assertEquals(WebSocketState.Active, webSocketCore.getConnectionState())
    }

    @Test
    fun `Given an Active connection When URL changes to a different URL Then it cancels the connection`() = runTest {
        val urlStateFlow = MutableStateFlow<UrlState>(UrlState.HasUrl("https://io.ha".toHttpUrlOrNull()?.toUrl()))
        setupServer(urlFlow = urlStateFlow, backgroundScope = backgroundScope)
        prepareAuthenticationAnswer()
        mockCancelTriggersOnFailure()

        assertTrue(webSocketCore.connect())
        assertEquals(WebSocketState.Active, webSocketCore.getConnectionState())

        urlStateFlow.value = UrlState.HasUrl("https://new.io.ha".toHttpUrlOrNull()?.toUrl())
        advanceUntilIdle()

        verify { mockConnection.cancel() }
        assertEquals(WebSocketState.ClosedUrlChange, webSocketCore.getConnectionState())
    }

    @Test
    fun `Given an Active connection When state changes to InsecureState Then it cancels without attempt to create a new socket`() = runTest {
        val urlStateFlow = MutableStateFlow<UrlState>(UrlState.HasUrl("https://io.ha".toHttpUrlOrNull()?.toUrl()))
        setupServer(urlFlow = urlStateFlow, backgroundScope = backgroundScope)
        prepareAuthenticationAnswer()
        mockCancelTriggersOnFailure()

        assertTrue(webSocketCore.connect())
        assertEquals(WebSocketState.Active, webSocketCore.getConnectionState())

        var reconnectAttempts = false
        every { mockOkHttpClient.newWebSocket(any(), any()) } answers {
            reconnectAttempts = true
            mockConnection
        }

        urlStateFlow.value = UrlState.InsecureState
        advanceUntilIdle()

        verify { mockConnection.cancel() }
        assertEquals(WebSocketState.ClosedUrlChange, webSocketCore.getConnectionState())
        assertFalse(reconnectAttempts)
    }

    @Test
    fun `Given an Active connection When URL emits the same value Then it does not cancel the connection`() = runTest {
        val initialUrl = "https://io.ha".toHttpUrlOrNull()?.toUrl()
        val urlStateFlow = MutableStateFlow<UrlState>(UrlState.HasUrl(initialUrl))
        setupServer(urlFlow = urlStateFlow, backgroundScope = backgroundScope)
        prepareAuthenticationAnswer()

        assertTrue(webSocketCore.connect())
        assertEquals(WebSocketState.Active, webSocketCore.getConnectionState())

        // Emit the same URL again
        urlStateFlow.value = UrlState.HasUrl(initialUrl)
        advanceUntilIdle()

        verify(exactly = 0) { mockConnection.cancel() }
        assertEquals(WebSocketState.Active, webSocketCore.getConnectionState())

        // Clean up by closing the connection to stop the URL observer
        closeConnection()
        advanceUntilIdle()
    }

    @Test
    fun `Given URL flow completes without emitting before connection established When connect is invoked Then it returns false`() = runTest {
        setupServer(urlFlow = emptyFlow())

        val result = webSocketCore.connect()

        assertFalse(result)
    }

    /*
sendMessage()
     */

    @Test
    fun `Given an Active connection that responds to a request When sendMessage is invoked Then it returns a socketResponse and removes the Active message`() = runTest {
        setupServer()
        prepareAuthenticationAnswer()
        assertTrue(webSocketCore.connect())
        val request = mapOf("type" to "test")
        val expectedMessageSent = kotlinJsonMapper.encodeToString<Map<String, Any>>(MapAnySerializer, request.plus("id" to 2))

        mockResultSuccessForId(2)

        val response = webSocketCore.sendMessage(request)

        coVerify { mockConnection.send(expectedMessageSent) }
        assertNotNull(response)
        assertEquals(2, response.id)
        assertTrue(response is MessageSocketResponse)
        assertTrue(webSocketCore.activeMessages.isEmpty())
    }

    @Test
    fun `Given an Active connection that does not respond within timeout When sendMessage is invoked Then it returns null`() = runTest {
        setupServer()
        prepareAuthenticationAnswer()
        assertTrue(webSocketCore.connect())
        val request = mapOf("type" to "test")
        val expectedMessageSent = kotlinJsonMapper.encodeToString<Map<String, Any>>(MapAnySerializer, request.plus("id" to 2))

        val response = webSocketCore.sendMessage(request)

        coVerify { mockConnection.send(expectedMessageSent) }
        assertNull(response)
        assertTrue(webSocketCore.activeMessages.isEmpty())
    }

    @Test
    fun `Given a not connected connection When sendMessage is invoked Then it reconnects`() = runTest {
        setupServer()
        prepareAuthenticationAnswer()

        assertEquals(WebSocketState.Initial, webSocketCore.getConnectionState())

        webSocketCore.sendMessage(mapOf("type" to "test"))

        assertEquals(WebSocketState.Active, webSocketCore.getConnectionState())
    }

    @Test
    fun `Given an invalid url  When sendMessage is invoked Then it returns null and connection state is ClosedOther`() = runTest {
        setupServer("an invalid url ")

        assertNull(webSocketCore.sendMessage(mapOf("type" to "test")))
        assertEquals(WebSocketState.ClosedOther, webSocketCore.getConnectionState())
    }

    @Test
    fun `Given a disconnection When sendMessage is invoked Then it returns null`() = runTest {
        setupServer()
        prepareAuthenticationAnswer()
        assertTrue(webSocketCore.connect())

        every { mockConnection.send(any<String>()) } answers {
            closeConnection()
            true
        }

        assertNull(webSocketCore.sendMessage(mapOf("type" to "test")))
    }

    @Test
    fun `Given an Active connection waiting for response When sendMessage coroutine is cancelled Then message is removed from activeMessages`() = runTest {
        setupServer()
        prepareAuthenticationAnswer()
        assertTrue(webSocketCore.connect())

        // Don't mock response so sendMessage hangs waiting for it
        every { mockConnection.send(match<String> { it.contains(""""id":2""") }) } returns true

        // Start sendMessage in a cancellable coroutine
        val sendJob = async {
            webSocketCore.sendMessage(mapOf("type" to "test"))
        }
        runCurrent()

        // Message should be in activeMessages while waiting for response
        assertEquals(1, webSocketCore.activeMessages.size)
        assertTrue(webSocketCore.activeMessages.containsKey(2L))

        // Cancel the sendMessage coroutine
        sendJob.cancel(CancellationException("Test cancellation"))
        runCurrent()

        // Message should be removed from activeMessages after cancellation
        assertTrue(webSocketCore.activeMessages.isEmpty())
    }

    @Test
    fun `Given reconnection to a closed connection When sendMessage is invoked Then the unique ID is not reset`() = runTest {
        setupServer()
        prepareAuthenticationAnswer()
        assertTrue(webSocketCore.connect())

        // use a big number so we don't mistake when asserting while counting for message of the auth flow
        repeat(5) {
            webSocketCore.sendMessage(mapOf("type" to "test"))
        }
        advanceUntilIdle()

        closeConnection()
        advanceUntilIdle()

        assertEquals(WebSocketState.ClosedOther, webSocketCore.getConnectionState())
        assertTrue(webSocketCore.activeMessages.isEmpty())

        assertTrue(webSocketCore.connect())
        assertEquals(WebSocketState.Active, webSocketCore.getConnectionState())

        val request = slot<String>()
        every { mockConnection.send(capture(request)) } answers { true }

        webSocketCore.sendMessage(mapOf("type" to "test"))
        advanceUntilIdle()

        // We sent 1 supported_features message then 5 messages then 1 supported_features message then 1 message
        assertTrue(request.captured.contains(""""id":8"""))
        assertTrue(webSocketCore.activeMessages.isEmpty())
    }

    /*
sendBytes()
     */

    @Test
    fun `Given an Active connection When sendBytes is invoked Then it returns the connection send result`() = runTest {
        setupServer()
        prepareAuthenticationAnswer()
        assertTrue(webSocketCore.connect())

        suspend fun assertSendBytes(success: Boolean) {
            every { mockConnection.send(any<ByteString>()) } returns success
            val result = webSocketCore.sendBytes(byteArrayOf(1, 2, 3))
            assertEquals(success, result)
        }

        assertSendBytes(true)
        assertSendBytes(false)
    }

    @Test
    fun `Given a not connected connection When sendBytes is invoked Then it reconnects`() = runTest {
        setupServer()
        prepareAuthenticationAnswer()

        assertEquals(WebSocketState.Initial, webSocketCore.getConnectionState())

        webSocketCore.sendBytes(byteArrayOf(1, 2, 3))

        assertEquals(WebSocketState.Active, webSocketCore.getConnectionState())
    }

    @Test
    fun `Given an invalid url When sendBytes is invoked Then it returns false and connection state is ClosedOther`() = runTest {
        setupServer("an invalid url ")

        assertFalse(webSocketCore.sendBytes(byteArrayOf(1, 2, 3)))
        assertEquals(WebSocketState.ClosedOther, webSocketCore.getConnectionState())
    }

    /*
subscribeTo
     */

    @Test
    fun `Given a connection When subscribeTo is invoked but the message is not sent properly Then it returns null and removes the message from activeMessages`() = runTest {
        setupServer()
        prepareAuthenticationAnswer()

        assertNull(
            webSocketCore.subscribeTo<String>(
                "test",
                mapOf("testSubscription" to "test"),
            ),
        )

        coVerify { mockConnection.send(match<String> { it.contains("testSubscription") }) }
        assertTrue(webSocketCore.activeMessages.isEmpty())
    }

    @Test
    fun `Given a connection When subscribeTo is invoked and the message is sent properly without any subscriber Then it returns a flow and keeps one Active message subscriber`() = runTest {
        setupServer()
        prepareAuthenticationAnswer()
        assertTrue(webSocketCore.connect())

        val expectedData: Map<String, Any> = mapOf("testSubscription" to "test")
        val expectedType = "testType"

        // Message sent by subscribeTo to request for events
        mockResultSuccessForId(2)

        assertNotNull(
            webSocketCore.subscribeTo<String>(
                expectedType,
                expectedData,
            ),
        )

        coVerify { mockConnection.send(match<String> { it.contains("testSubscription") }) }
        assertEquals(1, webSocketCore.activeMessages.size)
        webSocketCore.activeMessages.values.first().apply {
            assertTrue(this is ActiveMessage.Subscription)
            assertTrue(responseDeferred.isCompleted)
            assertEquals(expectedData.plus("type" to expectedType), (this as ActiveMessage.Subscription).request.message)
        }
    }

    @Test
    fun `Given a connection When subscribeTo is invoked with a subscriber Then it keeps one Active subscriber until unsubscribed, sends unsubscribe message, and closes the connection`() = runTest {
        // We need a dedicated scope with unconfined dispatcher that we can control to properly close the shared flow
        val subscriptionScope = TestScope(UnconfinedTestDispatcher())
        setupServer(backgroundScope = subscriptionScope)
        prepareAuthenticationAnswer()
        assertTrue(webSocketCore.connect())

        val expectedData: Map<String, String> = mapOf("testSubscription" to "test")
        val expectedType = "testType"

        // Message sent by subscribeTo to request for events
        mockResultSuccessForId(2)

        checkNotNull(
            webSocketCore.subscribeTo<String>(
                expectedType,
                expectedData,
            ),
        ).test {
            assertEquals(1, webSocketCore.activeMessages.size)

            // Message sent when unsubscribing to not block the coroutine
            mockResultSuccessForId(3)

            expectNoEvents()
            cancel()
            subscriptionScope.advanceUntilIdle()
        }

        advanceUntilIdle()
        assertTrue(webSocketCore.activeMessages.isEmpty())

        verifyOrder {
            mockConnection.send(match<String> { it.contains("testSubscription") && it.contains(""""id":2""") })
            mockConnection.send(match<String> { it.contains("unsubscribe_events") && it.contains(""""id":3""") })
        }

        verify(exactly = 1) { mockConnection.close(1001, "Done listening to subscriptions.") }
    }

    @Test
    fun `Given an Active subscription When unsubscribing Then subscription remains in activeMessages until unsubscribe is acknowledged`() = runTest {
        val subscriptionScope = TestScope(UnconfinedTestDispatcher())
        setupServer(backgroundScope = subscriptionScope)
        prepareAuthenticationAnswer()
        assertTrue(webSocketCore.connect())

        // Message sent by subscribeTo to request for events
        mockResultSuccessForId(2)

        // Track whether subscription was in activeMessages when unsubscribe was sent
        var subscriptionExistedDuringUnsubscribe = false

        // Mock unsubscribe message - verify subscription still exists when sending
        every {
            mockConnection.send(match<String> { it.contains("unsubscribe_events") && it.contains(""""id":3""") })
        } answers {
            // At this point, the subscription should still be in activeMessages
            subscriptionExistedDuringUnsubscribe = webSocketCore.activeMessages.containsKey(2L)
            webSocketListener.onMessage(
                mockConnection,
                """{"id":3,"type":"result","success":true,"result":null}""",
            )
            true
        }

        checkNotNull(
            webSocketCore.subscribeTo<String>("testType", mapOf("test" to "data")),
        ).test {
            // Subscription is Active
            assertEquals(1, webSocketCore.activeMessages.size)
            assertTrue(webSocketCore.activeMessages.containsKey(2L))

            cancel()
            subscriptionScope.advanceUntilIdle()
        }

        advanceUntilIdle()

        // Verify the subscription was still in activeMessages when unsubscribe message was sent
        assertTrue(subscriptionExistedDuringUnsubscribe, "Subscription should exist in activeMessages when unsubscribe is sent")

        // After unsubscribe completes, activeMessages should be empty
        assertTrue(webSocketCore.activeMessages.isEmpty())
    }

    @Test
    fun `Given a connection with an Active message When subscribeTo is invoked and unsubscribed Then it doesn't close the connection`() = runTest {
        // We need a dedicated scope with unconfined dispatcher that we can control to properly close the shared flow
        val subscriptionScope = TestScope(UnconfinedTestDispatcher())
        setupServer(backgroundScope = subscriptionScope)
        prepareAuthenticationAnswer()
        assertTrue(webSocketCore.connect())

        // Dummy request to create a fake active message
        webSocketCore.activeMessages[42] = ActiveMessage.Subscription(
            eventFlow = mockk(),
            onEvent = mockk(),
            responseDeferred = CompletableDeferred(),
            request = WebSocketRequest(emptyMap<String, String>()),
        )

        // Message sent by subscribeTo to request for events
        mockResultSuccessForId(2)

        checkNotNull(
            webSocketCore.subscribeTo<String>(
                "test",
                emptyMap(),
            ),
        ).test {
            assertEquals(2, webSocketCore.activeMessages.size)

            // Message sent when unsubscribing to not block the coroutine
            mockResultSuccessForId(3)

            expectNoEvents()
            cancel()
            subscriptionScope.advanceUntilIdle()
        }

        advanceUntilIdle()
        // We keep the fake active message only
        assertEquals(1, webSocketCore.activeMessages.size)
        assertTrue(webSocketCore.activeMessages.containsKey(42))

        verify(exactly = 0) { mockConnection.close(any(), any()) }
    }

    @Test
    fun `Given a connection When subscribeTo is invoked Then it emits events`() = runTest {
        setupServer()
        prepareAuthenticationAnswer()
        assertTrue(webSocketCore.connect())

        // Message sent by subscribeTo to request for events
        mockResultSuccessForId(2)

        checkNotNull(
            webSocketCore.subscribeTo<StateChangedEvent>(
                SUBSCRIBE_TYPE_SUBSCRIBE_EVENTS,
                mapOf("event_type" to "state_changed"),
            ),
        ).test {
            webSocketListener.onMessage(mockConnection, """{"id":2, "type":"event", "event":{"event_type":"state_changed", "time_fired":"2016-11-26T01:37:24.265429+00:00", "data": {"entity_id":"light.bed_light"}}}""")
            assertEquals("light.bed_light", awaitItem().entityId)
            webSocketListener.onMessage(mockConnection, """{"id":2, "type":"event", "event":{"event_type":"state_changed", "time_fired":"2016-11-26T01:37:24.265429+00:00", "data": {"entity_id":"light.bathroom"}}}""")
            assertEquals("light.bathroom", awaitItem().entityId)
        }
    }

    @Test
    fun `Given a connection with an existing subscription When subscribeTo is invoked for the same subscription Then it doesn't send a message again and unsubscribes only when the last subscriber unsubscribe`() = runTest {
        // We need a dedicated scope with unconfined dispatcher that we can control to properly close the shared flow
        val subscriptionScope = TestScope(UnconfinedTestDispatcher())
        setupServer(backgroundScope = subscriptionScope)
        prepareAuthenticationAnswer()
        assertTrue(webSocketCore.connect())

        // Message sent by subscribeTo to request for events
        mockResultSuccessForId(2)

        turbineScope {
            suspend fun createSubscription(name: String) = checkNotNull(
                webSocketCore.subscribeTo<StateChangedEvent>(
                    SUBSCRIBE_TYPE_SUBSCRIBE_EVENTS,
                    mapOf("event_type" to "state_changed"),
                ),
            ).testIn(this, name = name).apply {
                // We should only have one active message even with multiple subscription
                assertEquals(1, webSocketCore.activeMessages.size)
                coVerify(exactly = 1) { mockConnection.send(match<String> { it.contains("state_changed") }) }
            }

            val mainFlow = createSubscription("Main flow")

            webSocketListener.onMessage(mockConnection, """{"id":2, "type":"event", "event":{"event_type":"state_changed", "time_fired":"2016-11-26T01:37:24.265429+00:00", "data": {"entity_id":"light.bed_light"}}}""")
            assertEquals("light.bed_light", mainFlow.awaitItem().entityId)

            val secondaryFlow = createSubscription("Secondary flow")

            // event are not replayed when subscribing so we should not get any events here
            secondaryFlow.expectNoEvents()

            // event are received on both flow
            webSocketListener.onMessage(mockConnection, """{"id":2, "type":"event", "event":{"event_type":"state_changed", "time_fired":"2016-11-26T01:37:28.265429+00:00", "data": {"entity_id":"light.kitchen"}}}""")
            assertEquals("light.kitchen", mainFlow.awaitItem().entityId)
            assertEquals("light.kitchen", secondaryFlow.awaitItem().entityId)

            // manually cancel the flow to stop the subscription to the hot flow
            mainFlow.cancel()

            // there is still an active subscription so we should not unsubscribe
            verify(exactly = 0) { mockConnection.send(match<String> { it.contains("unsubscribe_events") }) }
            testScheduler.advanceUntilIdle()

            // we can still receive events on the secondary flow
            webSocketListener.onMessage(mockConnection, """{"id":2, "type":"event", "event":{"event_type":"state_changed", "time_fired":"2016-11-26T01:37:24.265429+00:00", "data": {"entity_id":"light.bathroom"}}}""")
            assertEquals("light.bathroom", secondaryFlow.awaitItem().entityId)

            secondaryFlow.cancel()
            testScheduler.advanceUntilIdle()
            // no subscription anymore and we should unsubscribe
            verify(exactly = 1) { mockConnection.send(match<String> { it.contains("unsubscribe_events") }) }
        }
    }

    @Test
    fun `Given an Active subscription When disconnection occurs Then it re-sends a request to get events`() = runTest {
        // The re-subscription happens in the background scope so we need to be able to control it
        val subscriptionScope = TestScope(UnconfinedTestDispatcher())
        setupServer(backgroundScope = subscriptionScope)
        prepareAuthenticationAnswer()
        assertTrue(webSocketCore.connect())

        // Message sent by subscribeTo to request for events
        mockResultSuccessForId(2)

        checkNotNull(
            webSocketCore.subscribeTo<StateChangedEvent>(
                SUBSCRIBE_TYPE_SUBSCRIBE_EVENTS,
                mapOf("event_type" to "state_changed"),
            ),
        ).test {
            webSocketListener.onMessage(mockConnection, """{"id":2, "type":"event", "event":{"event_type":"state_changed", "time_fired":"2016-11-26T01:37:24.265429+00:00", "data": {"entity_id":"light.bed_light"}}}""")
            assertEquals("light.bed_light", awaitItem().entityId)

            assertEquals(1, webSocketCore.activeMessages.size)

            closeConnection()

            // Message sent when re-subscribing to not block the coroutine
            mockResultSuccessForId(4)

            // wait for the connection to reopen and re-subscribe
            advanceUntilIdle()

            assertEquals(1, webSocketCore.activeMessages.size)
            assertTrue(webSocketCore.activeMessages.containsKey(4))

            // receiving a new message after reconnection on the same flow
            webSocketListener.onMessage(mockConnection, """{"id":4, "type":"event", "event":{"event_type":"state_changed", "time_fired":"2016-11-26T01:37:24.265429+00:00", "data": {"entity_id":"light.bathroom"}}}""")
            assertEquals("light.bathroom", awaitItem().entityId)
        }
    }

    /*
shutdown()
     */

    @Test
    fun `Given a connection When shutdown is invoked Then it closes the connection`() = runTest {
        setupServer()
        prepareAuthenticationAnswer()
        assertTrue(webSocketCore.connect())

        webSocketCore.shutdown()

        verify { mockConnection.close(1001, "Session removed from app.") }
    }

    /*
misc
     */

    @Test
    fun `Given an unknown event received When listening for messages Then it unsubscribes from the event`() = runTest {
        // The unsubscribes message is sent from the background so we need to control it
        val customScope = TestScope(UnconfinedTestDispatcher())

        setupServer(backgroundScope = customScope)
        prepareAuthenticationAnswer()
        assertTrue(webSocketCore.connect())

        // Message sent when unsubscribing to not block the coroutine
        mockResultSuccessForId(2)

        webSocketListener.onMessage(mockConnection, """{"id":42, "type":"event", "event":{"event_type":"state_changed", "time_fired":"2016-11-26T01:37:24.265429+00:00", "data": {"entity_id":"light.bed_light"}}}""")
        customScope.advanceUntilIdle()
        advanceUntilIdle()

        // Should gracefully unsubscribe rather than crash
        coVerify { mockConnection.send(match<String> { it.contains("unsubscribe_events") && it.contains(""""id":2""") }) }
    }

    /*
    URL change reconnection behavior
     */

    @Test
    fun `Given an Active subscription When URL changes Then it reconnects immediately without delay`() = runTest {
        val urlFlow = MutableStateFlow<UrlState>(UrlState.HasUrl("https://io.ha".toHttpUrlOrNull()?.toUrl()))
        setupServer(urlFlow = urlFlow, backgroundScope = backgroundScope)
        prepareAuthenticationAnswer()
        assertTrue(webSocketCore.connect())
        mockCancelTriggersOnFailure()

        // Create a subscription so reconnection is triggered
        mockResultSuccessForId(2)
        checkNotNull(
            webSocketCore.subscribeTo<StateChangedEvent>(
                SUBSCRIBE_TYPE_SUBSCRIBE_EVENTS,
                mapOf("event_type" to "state_changed"),
            ),
        ).test {
            assertEquals(1, webSocketCore.activeMessages.size)

            // Track reconnection attempts (first call was during initial connect)
            var reconnectAttemptCount = 0
            every { mockOkHttpClient.newWebSocket(any(), any()) } answers {
                reconnectAttemptCount++
                mockConnection
            }

            // Simulate URL change - should trigger immediate reconnection (no 10s delay)
            urlFlow.value = UrlState.HasUrl("https://new.url".toHttpUrlOrNull()?.toUrl())

            // runCurrent() processes immediate work without advancing virtual time.
            // Since URL change reconnection has no delay, this should trigger reconnection.
            runCurrent()

            // Reconnection should have already been attempted (no delay)
            assertEquals(1, reconnectAttemptCount)

            // Clean up by closing the connection
            closeConnection()
            advanceUntilIdle()
        }
    }

    @Test
    fun `Given an Active subscription When disconnection occurs Then it waits before reconnecting`() = runTest {
        setupServer(backgroundScope = backgroundScope)
        prepareAuthenticationAnswer()
        assertTrue(webSocketCore.connect())

        // Create a subscription so reconnection is triggered
        mockResultSuccessForId(2)
        checkNotNull(
            webSocketCore.subscribeTo<StateChangedEvent>(
                SUBSCRIBE_TYPE_SUBSCRIBE_EVENTS,
                mapOf("event_type" to "state_changed"),
            ),
        ).test {
            assertEquals(1, webSocketCore.activeMessages.size)

            // Track reconnection attempts (first call was during initial connect)
            var reconnectAttemptCount = 0
            every { mockOkHttpClient.newWebSocket(any(), any()) } answers {
                reconnectAttemptCount++
                mockConnection
            }

            // Normal disconnection (not URL change)
            closeConnection()

            // Immediately after close, no reconnection should have happened yet (due to 10s delay)
            runCurrent()
            assertEquals(0, reconnectAttemptCount, "Should not reconnect immediately on normal close")

            // After the delay passes, reconnection should happen
            advanceUntilIdle()

            assertEquals(1, reconnectAttemptCount)

            // Clean up
            closeConnection()
            advanceUntilIdle()
        }
    }

    /*
    Concurrency tests
     */

    @Test
    fun `Given an Active connection When multiple sendMessage calls are made concurrently Then messages are serialized in order`() = runTest {
        setupServer()
        prepareAuthenticationAnswer()
        assertTrue(webSocketCore.connect())

        val sentMessages = mutableListOf<String>()
        every { mockConnection.send(match<String> { it.contains(""""type":"test"""") }) } answers {
            sentMessages.add(firstArg())
            // Simulate response for each message
            val id = Regex(""""id":(\d+)""").find(firstArg<String>())?.groupValues?.get(1)?.toLong()
            if (id != null) {
                webSocketListener.onMessage(
                    mockConnection,
                    """{"id":$id,"type":"result","success":true,"result":{}}""",
                )
            } else {
                fail { "Missing id" }
            }
            true
        }

        // Launch multiple sendMessage calls concurrently
        val nbMessages = 50
        val jobs = (1..nbMessages).map { i ->
            async {
                delay(deterministicDelay(i.toLong()))
                webSocketCore.sendMessage(mapOf("type" to "test", "value" to i))
            }
        }

        // Wait for all to complete
        jobs.awaitAll()
        advanceUntilIdle()

        // Verify all messages were sent
        assertEquals(nbMessages, sentMessages.size)

        // Verify IDs are sequential (starting from 2, after supported_features which is 1)
        val ids = sentMessages.map { msg ->
            Regex(""""id":(\d+)""").find(msg)?.groupValues?.get(1)?.toLong()
        }
        assertEquals((2L..51L).toList(), ids)

        // Verify the "value" field is NOT in sequential order (proving async execution)
        val values = sentMessages.map { msg ->
            Regex(""""value":(\d+)""").find(msg)?.groupValues?.get(1)?.toInt()
        }
        // Values should contain all 1..50 but NOT in order (due to random delays)
        assertEquals((1..50).toSet(), values.toSet())
        assertNotEquals((1..50).toList(), values, "Values should not be in sequential order - async execution should shuffle them")
    }

    @Test
    fun `Given no connection When multiple connect calls are made Then only one connection is created`() = runTest {
        setupServer()
        prepareAuthenticationAnswer()

        // Launch multiple connect calls concurrently
        val jobs = (1..50).map { i ->
            async {
                delay(deterministicDelay(i.toLong()))
                webSocketCore.connect()
            }
        }

        // Wait for all to complete
        val results = jobs.awaitAll()

        // All should succeed
        assertTrue(results.all { it })

        // Only one WebSocket should be created
        verify(exactly = 1) { mockOkHttpClient.newWebSocket(any(), any()) }
    }

    @Test
    fun `Given subscription setup in progress When coroutine is cancelled Then activeMessages is cleaned up`() = runTest {
        setupServer()
        prepareAuthenticationAnswer()
        assertTrue(webSocketCore.connect())

        // Don't mock response so subscribeTo hangs waiting for confirmation
        every { mockConnection.send(match<String> { it.contains("test_subscription") }) } returns true

        val subscribeJob = async {
            webSocketCore.subscribeTo<String>(
                "test_subscription",
                emptyMap(),
            )
        }
        runCurrent()

        // auth - supported_features - test_subscription
        verify(exactly = 3) { mockConnection.send(any<String>()) }

        // Message should be in activeMessages while waiting for confirmation
        assertEquals(1, webSocketCore.activeMessages.size)

        // Cancel the subscription coroutine
        subscribeJob.cancel()
        runCurrent()

        // activeMessages should be cleaned up
        assertTrue(webSocketCore.activeMessages.isEmpty())

        verify(exactly = 3) { mockConnection.send(any<String>()) }
    }

    @Test
    fun `Given a message waiting for response When response arrives after timeout Then it is handled gracefully`() = runTest {
        setupServer()
        prepareAuthenticationAnswer()
        assertTrue(webSocketCore.connect())

        every { mockConnection.send(match<String> { it.contains(""""id":2""") }) } returns true

        // Use a short timeout for the test
        val timeout = 5.seconds
        val sendJob = async {
            webSocketCore.sendMessage(WebSocketRequest(mapOf("type" to "test"), timeout))
        }
        runCurrent()

        // Message should be in activeMessages while waiting
        assertEquals(1, webSocketCore.activeMessages.size)

        // Advance time but not past timeout - should still be waiting
        advanceTimeBy(timeout.minus(1.seconds))
        assertFalse(sendJob.isCompleted, "Should still be waiting before timeout")
        assertEquals(1, webSocketCore.activeMessages.size)

        // Advance past timeout
        advanceTimeBy(2.seconds)
        assertTrue(sendJob.isCompleted, "Should complete after timeout")

        val response = sendJob.await()
        assertNull(response)
        // activeMessages should be cleaned up after timeout
        assertTrue(webSocketCore.activeMessages.isEmpty())

        // Now send a late response - should not cause any issues
        webSocketListener.onMessage(
            mockConnection,
            """{"id":2,"type":"result","success":true,"result":{}}""",
        )
        advanceUntilIdle()

        // Should still be empty
        assertTrue(webSocketCore.activeMessages.isEmpty())
    }

    @Test
    fun `Given a message waiting for response When response arrives after cancel Then it is handled gracefully`() = runTest {
        setupServer()
        prepareAuthenticationAnswer()
        assertTrue(webSocketCore.connect())

        every { mockConnection.send(match<String> { it.contains(""""id":2""") }) } returns true

        // Use a short timeout for the test
        val timeout = 5.seconds
        val sendJob = async {
            webSocketCore.sendMessage(WebSocketRequest(mapOf("type" to "test"), timeout))
        }
        runCurrent()

        // Message should be in activeMessages while waiting
        assertEquals(1, webSocketCore.activeMessages.size)

        // Advance time but not past timeout - should still be waiting
        advanceTimeBy(timeout.minus(1.seconds))
        assertFalse(sendJob.isCompleted, "Should still be waiting before timeout")
        assertEquals(1, webSocketCore.activeMessages.size)

        sendJob.cancel()
        runCurrent()

        // activeMessages should be cleaned up after canceling
        assertTrue(webSocketCore.activeMessages.isEmpty())

        // Now send a late response - should not cause any issues
        webSocketListener.onMessage(
            mockConnection,
            """{"id":2,"type":"result","success":true,"result":{}}""",
        )
        advanceUntilIdle()

        // Should still be empty
        assertTrue(webSocketCore.activeMessages.isEmpty())
    }

    @Test
    fun `Given connection becomes null during send When sendMessage is invoked Then it returns null`() = runTest {
        setupServer()
        prepareAuthenticationAnswer()
        assertTrue(webSocketCore.connect())

        // Close connection when trying to send
        every { mockConnection.send(match<String> { it.contains(""""type":"test"""") }) } answers {
            closeConnection()
            true
        }

        val response = webSocketCore.sendMessage(mapOf("type" to "test"))

        assertNull(response)
        assertTrue(webSocketCore.activeMessages.isEmpty())
    }

    @Test
    fun `Given a message received response When duplicate response arrives Then warning is logged and no crash occurs`() = runTest {
        setupServer()
        prepareAuthenticationAnswer()
        assertTrue(webSocketCore.connect())

        // First response
        every { mockConnection.send(match<String> { it.contains(""""id":2""") }) } answers {
            webSocketListener.onMessage(
                mockConnection,
                """{"id":2,"type":"result","success":true,"result":{}}""",
            )
            true
        }

        val response = webSocketCore.sendMessage(mapOf("type" to "test"))
        assertNotNull(response)
        advanceUntilIdle()

        // activeMessages should be cleaned up
        assertTrue(webSocketCore.activeMessages.isEmpty())

        // Send duplicate response - should be handled gracefully
        webSocketListener.onMessage(
            mockConnection,
            """{"id":2,"type":"result","success":true,"result":{"duplicate":true}}""",
        )
        advanceUntilIdle()

        // still empty
        assertTrue(webSocketCore.activeMessages.isEmpty())
    }

    @Test
    fun `Given URL changes to null while authenticating When connect in progress Then it fails connection`() = runTest {
        val url1 = "https://first.url".toHttpUrlOrNull()?.toUrl()
        val urlFlow = MutableStateFlow<UrlState>(UrlState.HasUrl(url1))
        setupServer(urlFlow = urlFlow, backgroundScope = backgroundScope)

        // Auth message sent but don't respond - URL changes to null during auth
        every { mockConnection.send(match<String> { it.contains(""""type":"auth"""") }) } answers {
            urlFlow.value = UrlState.HasUrl(null)
            runCurrent()
            true
        }
        mockCancelTriggersOnFailure()

        val result = webSocketCore.connect()

        assertFalse(result, "Should fail when URL becomes null during auth")
        assertEquals(WebSocketState.ClosedOther, webSocketCore.getConnectionState())
    }

    @Test
    fun `Given InsecureState while authenticating When connect in progress Then it fails connection`() = runTest {
        val url1 = "https://first.url".toHttpUrlOrNull()?.toUrl()
        val urlFlow = MutableStateFlow<UrlState>(UrlState.HasUrl(url1))
        setupServer(urlFlow = urlFlow, backgroundScope = backgroundScope)

        // Auth message sent but don't respond - state changes to InsecureState during auth
        every { mockConnection.send(match<String> { it.contains(""""type":"auth"""") }) } answers {
            urlFlow.value = UrlState.InsecureState
            runCurrent()
            true
        }
        mockCancelTriggersOnFailure()

        val result = webSocketCore.connect()

        assertFalse(result, "Should fail when state becomes InsecureState during auth")
        assertEquals(WebSocketState.ClosedOther, webSocketCore.getConnectionState())
    }

    @Test
    fun `Given network failure after auth sent When onFailure called Then connect fails gracefully`() = runTest {
        setupServer()

        // Auth message sent successfully
        every { mockConnection.send(match<String> { it.contains(""""type":"auth"""") }) } returns true

        // Start connecting
        val connectJob = async { webSocketCore.connect() }
        advanceUntilIdle()

        // Network fails after auth was sent (async, as in real OkHttp)
        webSocketListener.onFailure(mockConnection, IOException("Network error"), null)
        advanceUntilIdle()

        assertFalse(connectJob.await())
        assertEquals(WebSocketState.ClosedOther, webSocketCore.getConnectionState())
    }

    @Test
    fun `Given auth timeout When 30 seconds pass without auth response Then connect fails`() = runTest {
        setupServer()

        // Auth message sent but never responds
        every { mockConnection.send(match<String> { it.contains(""""type":"auth"""") }) } returns true
        mockCancelTriggersOnFailure()

        val result = webSocketCore.connect()

        assertFalse(result, "Should fail after auth timeout")
        verify { mockConnection.cancel() }
    }

    @Test
    fun `Given auth fails When reconnecting with valid auth Then it connects`() = runTest {
        setupServer()
        mockCancelTriggersOnFailure()

        // First connect: auth fails
        prepareAuthenticationAnswer(successfulAuth = false)

        val firstResult = webSocketCore.connect()
        assertFalse(firstResult)
        assertEquals(WebSocketState.ClosedAuth, webSocketCore.getConnectionState())

        // Wait for cleanup coroutine to clear the connection state
        advanceUntilIdle()

        // Second connect should be able to try again
        prepareAuthenticationAnswer(successfulAuth = true)

        val secondResult = webSocketCore.connect()
        assertTrue(secondResult)
        assertEquals(WebSocketState.Active, webSocketCore.getConnectionState())

        closeConnection()
    }

    @Test
    fun `Given sendMessage called while authenticating When auth not complete Then it waits for auth and sends`() = runTest {
        setupServer()

        // Auth message sent but delayed response - trigger onOpen to transition to Authenticating
        every { mockConnection.send(match<String> { it.contains(""""type":"auth"""") }) } answers {
            // Trigger onOpen to transition state to Authenticating
            webSocketListener.onOpen(mockConnection, mockk(relaxed = true))
            // Don't complete auth immediately - will be completed later
            true
        }

        // Start connect but don't complete auth
        val connectJob = async { webSocketCore.connect() }
        runCurrent()

        assertEquals(WebSocketState.Authenticating, webSocketCore.getConnectionState())

        // Try to send a message while authenticating - this should wait
        val sendJob = async {
            webSocketCore.sendMessage(mapOf("type" to "test"))
        }
        runCurrent()

        // Neither should be complete yet
        assertFalse(connectJob.isCompleted, "Connect should still be waiting for auth")
        assertFalse(sendJob.isCompleted, "Send should wait for connection")

        // Now complete auth
        mockResultSuccessForId(2)
        webSocketListener.onMessage(
            mockConnection,
            """{"type":"auth_ok","ha_version":"2025.4.1"}""",
        )
        advanceUntilIdle()

        // Both should complete now
        assertTrue(connectJob.await())
        assertNotNull(sendJob.await())
    }

    @Test
    fun `Given Active subscription When connection is lost Then subscription is maintained for reconnection`() = runTest {
        setupServer(backgroundScope = backgroundScope)
        prepareAuthenticationAnswer()
        assertTrue(webSocketCore.connect())

        // Create subscription
        mockResultSuccessForId(2)
        val subscription = checkNotNull(
            webSocketCore.subscribeTo<StateChangedEvent>(
                SUBSCRIBE_TYPE_SUBSCRIBE_EVENTS,
                mapOf("event_type" to "state_changed"),
            ),
        )

        subscription.test {
            // Verify subscription is active and tracked
            assertEquals(1, webSocketCore.activeMessages.size)
            assertTrue(
                webSocketCore.activeMessages.any { it.value is ActiveMessage.Subscription },
                "Subscription should be tracked in activeMessages",
            )

            // Receive an event to confirm subscription works
            webSocketListener.onMessage(
                mockConnection,
                """{"id":2, "type":"event", "event":{"event_type":"state_changed", "time_fired":"2016-11-26T01:37:24.265429+00:00", "data": {"entity_id":"light.bed_light"}}}""",
            )
            assertEquals("light.bed_light", awaitItem().entityId)

            // Simulate connection loss
            closeConnection()

            // Verify subscription is still maintained in activeMessages for reconnection
            // (reconnection itself is tested in `Given an Active subscription When disconnection occurs Then it re-sends a request to get events`)
            assertTrue(
                webSocketCore.activeMessages.any { it.value is ActiveMessage.Subscription },
                "Subscription should still be tracked after connection loss for reconnection",
            )
        }
    }

    @Test
    fun `Given pending simple messages When connection closes during URL change Then they complete with exception`() = runTest {
        val urlFlow = MutableStateFlow<UrlState>(UrlState.HasUrl("https://io.ha".toHttpUrlOrNull()?.toUrl()))
        setupServer(urlFlow = urlFlow, backgroundScope = backgroundScope)
        prepareAuthenticationAnswer()
        assertTrue(webSocketCore.connect())

        // Don't respond to messages so they stay pending
        every { mockConnection.send(match<String> { it.contains(""""type":"test"""") }) } returns true
        mockCancelTriggersOnFailure()

        // Start a message that won't get a response
        val sendJob = async {
            webSocketCore.sendMessage(mapOf("type" to "test"))
        }
        runCurrent()

        // Message should be pending
        assertEquals(1, webSocketCore.activeMessages.size)

        // Change URL to trigger disconnect
        urlFlow.value = UrlState.HasUrl("https://new.url".toHttpUrlOrNull()?.toUrl())
        advanceUntilIdle()

        // Message should complete with null (due to exception)
        val result = sendJob.await()
        assertNull(result, "Pending message should return null when connection closes")
        assertTrue(webSocketCore.activeMessages.isEmpty(), "Active messages should be cleared")
    }

    @Test
    fun `Given URL observer Active When shutdown called Then URL observer is cancelled`() = runTest {
        val urlFlow = MutableStateFlow<UrlState>(UrlState.HasUrl("https://io.ha".toHttpUrlOrNull()?.toUrl()))
        setupServer(urlFlow = urlFlow, backgroundScope = backgroundScope)
        prepareAuthenticationAnswer()
        assertTrue(webSocketCore.connect())

        // Make connection.close() trigger onClosed callback
        every { mockConnection.close(any(), any()) } answers {
            webSocketListener.onClosed(mockConnection, firstArg(), secondArg())
            true
        }

        // Shutdown calls connection.close() which triggers handleClosingSocket and cancels URL observer
        webSocketCore.shutdown()
        advanceUntilIdle()

        // URL changes after shutdown should not affect anything
        var reconnectAttempts = false
        every { mockOkHttpClient.newWebSocket(any(), any()) } answers {
            reconnectAttempts = true
            mockConnection
        }

        urlFlow.value = UrlState.HasUrl("https://new.url".toHttpUrlOrNull()?.toUrl())
        advanceUntilIdle()

        // Should not trigger reconnection since observer was cancelled
        assertFalse(reconnectAttempts, "URL observer should be cancelled after shutdown")
    }
}
