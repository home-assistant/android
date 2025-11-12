package io.homeassistant.companion.android.common.data.websocket.impl

import app.cash.turbine.test
import app.cash.turbine.turbineScope
import io.homeassistant.companion.android.common.data.authentication.AuthenticationRepository
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.data.websocket.WebSocketRequest
import io.homeassistant.companion.android.common.data.websocket.WebSocketState
import io.homeassistant.companion.android.common.data.websocket.impl.WebSocketConstants.SUBSCRIBE_TYPE_SUBSCRIBE_EVENTS
import io.homeassistant.companion.android.common.data.websocket.impl.entities.MessageSocketResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.StateChangedEvent
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.api.assertNull
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(ConsoleLogExtension::class)
class WebSocketCoreImplTest {
    private lateinit var mockOkHttpClient: OkHttpClient
    private lateinit var mockConnection: WebSocket
    private lateinit var webSocketCore: WebSocketCoreImpl
    private lateinit var webSocketListener: WebSocketListener

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    private fun TestScope.setupServer(
        url: String = "https://io.ha",
        backgroundScope: CoroutineScope = this.backgroundScope,
    ) {
        mockOkHttpClient = mockk<OkHttpClient>(relaxed = true)
        val mockServerManager = mockk<ServerManager>(relaxed = true)
        val mockAuthenticationRepository = mockk<AuthenticationRepository>(relaxed = true)

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
        coEvery { mockAuthenticationRepository.retrieveAccessToken() } returns "mock_access_token"
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
            assertSame(WebSocketState.AUTHENTICATING, webSocketCore.getConnectionState())
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

    /*
connect()
     */

    @ParameterizedTest
    @ValueSource(strings = ["", "htt://io.ha", "ws://io.ha", "wss://io.ha"])
    fun `Given invalid url When connect is invoked Then it returns false and connection state is null`(
        url: String,
    ) = runTest {
        setupServer(url)

        val result = webSocketCore.connect()

        assertFalse(result)
        assertNull(webSocketCore.getConnectionState())
    }

    @Test
    fun `Given no server When connect is invoked Then it returns false and connection state is null`() = runTest {
        val serverManager = mockk<ServerManager>(relaxed = true)
        coEvery { serverManager.getServer(any<Int>()) } returns null

        val webSocketCore = WebSocketCoreImpl(
            okHttpClient = mockk(),
            serverManager = serverManager,
            serverId = 1,
        )

        val result = webSocketCore.connect()

        assertFalse(result)
        assertNull(webSocketCore.getConnectionState())
    }

    @Test
    fun `Given failure to send auth message after socket creation When connect is invoked Then it returns false and connection state is null`() = runTest {
        setupServer()
        every { mockConnection.send(any<String>()) } returns false

        val result = webSocketCore.connect()

        assertFalse(result)
        assertNull(webSocketCore.getConnectionState())
    }

    @Test
    fun `Given failure at socket creation When connect is invoked Then it returns false and connection state is null`() = runTest {
        setupServer()
        // Simulate a failure while creating the socket
        every { mockOkHttpClient.newWebSocket(any(), any()) } throws IllegalStateException()

        val result = webSocketCore.connect()

        assertFalse(result)
        assertNull(webSocketCore.getConnectionState())
    }

    @ParameterizedTest
    @ValueSource(strings = ["http://io.ha", "https://io.ha", "http://192.168.0.42:8123", "https://192.168.0.42:8123"])
    fun `Given valid url When connect is invoked Then it returns true and connection state is ACTIVE`(
        url: String,
    ) = runTest {
        setupServer(url)
        prepareAuthenticationAnswer()

        val result = webSocketCore.connect()
        assertTrue(result)
        assertSame(WebSocketState.ACTIVE, webSocketCore.getConnectionState())
    }

    @Test
    fun `Given invalid authentication When connect is invoked Then it returns false and connection state is CLOSED_AUTH`() = runTest {
        setupServer()
        prepareAuthenticationAnswer(successfulAuth = false)

        val result = webSocketCore.connect()

        assertFalse(result)
        assertSame(WebSocketState.CLOSED_AUTH, webSocketCore.getConnectionState())
    }

    @Test
    fun `Given a valid configuration for 2023 1 1 server When connect is invoked Then it returns true, sends the supported_features message, and connection state is ACTIVE`() = runTest {
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
        assertSame(WebSocketState.ACTIVE, webSocketCore.getConnectionState())
    }

    @Test
    fun `Given a valid configuration for 2020 1 1 server When connect is invoked Then it returns true and connection state is ACTIVE`() = runTest {
        setupServer()
        prepareAuthenticationAnswer("2020.1.1")

        val result = webSocketCore.connect()

        assertTrue(result)
        // auth
        coVerify(exactly = 1) { mockConnection.send(any<String>()) }
        assertSame(WebSocketState.ACTIVE, webSocketCore.getConnectionState())
    }

    /*
sendMessage()
     */

    @Test
    fun `Given an active connection that responds to a request When sendMessage is invoked Then it returns a socketResponse and removes the active message`() = runTest {
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
        assertEquals(emptyMap<Long, WebSocketRequest>(), webSocketCore.activeMessages)
    }

    @Test
    fun `Given an active connection that does not respond within timeout When sendMessage is invoked Then it returns null`() = runTest {
        setupServer()
        prepareAuthenticationAnswer()
        assertTrue(webSocketCore.connect())
        val request = mapOf("type" to "test")
        val expectedMessageSent = kotlinJsonMapper.encodeToString<Map<String, Any>>(MapAnySerializer, request.plus("id" to 2))

        val response = webSocketCore.sendMessage(request)

        coVerify { mockConnection.send(expectedMessageSent) }
        assertNull(response)
        assertEquals(emptyMap<Long, WebSocketRequest>(), webSocketCore.activeMessages)
    }

    @Test
    fun `Given a not connected connection When sendMessage is invoked Then it reconnects`() = runTest {
        setupServer()
        prepareAuthenticationAnswer()

        assertNull(webSocketCore.getConnectionState())

        webSocketCore.sendMessage(mapOf("type" to "test"))

        assertEquals(WebSocketState.ACTIVE, webSocketCore.getConnectionState())
    }

    @Test
    fun `Given an invalid url  When sendMessage is invoked Then it returns null and connection state remains null`() = runTest {
        setupServer("an invalid url ")

        assertNull(webSocketCore.sendMessage(mapOf("type" to "test")))
        assertNull(webSocketCore.getConnectionState())
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

        assertEquals(WebSocketState.CLOSED_OTHER, webSocketCore.getConnectionState())
        assertTrue(webSocketCore.activeMessages.isEmpty())

        assertTrue(webSocketCore.connect())
        assertEquals(WebSocketState.ACTIVE, webSocketCore.getConnectionState())

        val request = slot<String>()
        every { mockConnection.send(capture(request)) } answers { true }

        webSocketCore.sendMessage(mapOf("type" to "test"))
        advanceUntilIdle()

        // We sent 1 supported_features message then 5 messages then 1 supported_features message then 1 message
        assertTrue(request.captured.contains(""""id":8"""))
        assertEquals(emptyMap<Long, WebSocketRequest>(), webSocketCore.activeMessages)
    }

    /*
sendBytes()
     */

    @Test
    fun `Given an active connection When sendBytes is invoked Then it returns the connection send result`() = runTest {
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

        assertNull(webSocketCore.getConnectionState())

        webSocketCore.sendBytes(byteArrayOf(1, 2, 3))

        assertEquals(WebSocketState.ACTIVE, webSocketCore.getConnectionState())
    }

    @Test
    fun `Given an invalid url When sendBytes is invoked Then it returns null`() = runTest {
        setupServer("an invalid url ")

        assertNull(webSocketCore.sendBytes(byteArrayOf(1, 2, 3)))
        assertNull(webSocketCore.getConnectionState())
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
        assertEquals(emptyMap<Long, WebSocketRequest>(), webSocketCore.activeMessages)
    }

    @Test
    fun `Given a connection When subscribeTo is invoked and the message is sent properly without any subscriber Then it returns a flow and keeps one active message subscriber`() = runTest {
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
            // asserts the internal of the implementation to check that the active message is an event
            assertNotNull(eventFlow)
            assertNotNull(onEvent)
            assertNotNull(onResponse)
            assertTrue(onResponse!!.isCompleted)
            assertNotNull(message)
            assertEquals(expectedData.plus("type" to expectedType), message)
        }
    }

    @Test
    fun `Given a connection When subscribeTo is invoked with a subscriber Then it keeps one active subscriber until unsubscribed, sends unsubscribe message, and closes the connection`() = runTest {
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
        assertEquals(emptyMap<Long, WebSocketRequest>(), webSocketCore.activeMessages)

        verifyOrder {
            mockConnection.send(match<String> { it.contains("testSubscription") && it.contains(""""id":2""") })
            mockConnection.send(match<String> { it.contains("unsubscribe_events") && it.contains(""""id":3""") })
        }

        verify(exactly = 1) { mockConnection.close(1001, "Done listening to subscriptions.") }
    }

    @Test
    fun `Given a connection with an active message When subscribeTo is invoked and unsubscribed Then it doesn't close the connection`() = runTest {
        // We need a dedicated scope with unconfined dispatcher that we can control to properly close the shared flow
        val subscriptionScope = TestScope(UnconfinedTestDispatcher())
        setupServer(backgroundScope = subscriptionScope)
        prepareAuthenticationAnswer()
        assertTrue(webSocketCore.connect())

        // Dummy request to create a fake active message
        webSocketCore.activeMessages[42] = WebSocketRequest(emptyMap<String, String>())

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
    fun `Given an active subscription When disconnection occurs Then it re-sends a request to get events`() = runTest {
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
        coVerify { mockConnection.send(match<String> { it.contains("unsubscribe_events") && it.contains(""""id":2""") }) }
    }
}
