package io.homeassistant.companion.android.frontend.webrtc

import android.media.AudioManager
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.frontend.externalbus.FrontendExternalBusRepository
import io.homeassistant.companion.android.frontend.externalbus.frontendExternalBusJson
import io.homeassistant.companion.android.frontend.externalbus.incoming.WebRtcStreamType
import io.homeassistant.companion.android.frontend.externalbus.outgoing.OutgoingExternalBusMessage
import io.homeassistant.companion.android.frontend.permissions.PermissionManager
import io.homeassistant.companion.android.webrtc.core.AudioReceiver
import io.homeassistant.companion.android.webrtc.core.AudioSender
import io.homeassistant.companion.android.webrtc.core.ConnectionState
import io.homeassistant.companion.android.webrtc.core.MediaOptions
import io.homeassistant.companion.android.webrtc.core.RtcStatsSnapshot
import io.homeassistant.companion.android.webrtc.core.SessionFailure
import io.homeassistant.companion.android.webrtc.core.VideoReceiver
import io.homeassistant.companion.android.webrtc.core.WebRtcSession
import io.homeassistant.companion.android.webrtc.core.WebRtcSessionFactory
import io.homeassistant.companion.android.webrtc.core.signaling.SignalingClient
import io.homeassistant.companion.android.webrtc.signaling.HaSignalingClient
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private const val SERVER_ID = 1

private class FakeWebRtcSession : WebRtcSession {
    val stateFlow = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    override val state: StateFlow<ConnectionState> = stateFlow
    override val videoIn: VideoReceiver? = null
    var playbackEnabled: Boolean? = null
    override val audioIn: AudioReceiver = object : AudioReceiver {
        override suspend fun setPlaybackEnabled(enabled: Boolean) {
            playbackEnabled = enabled
        }
    }
    override val audioOut: AudioSender? = null
    var started = false
    var released = false

    override fun observeStats(interval: Duration): Flow<RtcStatsSnapshot> = emptyFlow()
    override suspend fun start() {
        started = true
    }
    override suspend fun stop() {}
    override suspend fun release() {
        released = true
    }
}

private class FakeWebRtcSessionFactory : WebRtcSessionFactory {
    val created = mutableListOf<FakeWebRtcSession>()
    var lastSignalingClient: SignalingClient? = null
    var lastMediaOptions: MediaOptions? = null

    /** When set, [create] suspends on it so a test can act while a session is being created. */
    var createGate: CompletableDeferred<Unit>? = null

    override suspend fun create(signalingClient: SignalingClient, mediaOptions: MediaOptions): WebRtcSession {
        createGate?.await()
        lastSignalingClient = signalingClient
        lastMediaOptions = mediaOptions
        return FakeWebRtcSession().also { created += it }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class WebRtcHandlerTest {

    private val factory = FakeWebRtcSessionFactory()
    private val serverManager: ServerManager = mockk(relaxed = true)
    private val permissionManager: PermissionManager = mockk()
    private val externalBus: FrontendExternalBusRepository = mockk(relaxed = true)
    private val audioManager: AudioManager = mockk(relaxed = true)
    private val sent = mutableListOf<OutgoingExternalBusMessage>()

    private fun TestScope.createHandler(): WebRtcHandler {
        coEvery { externalBus.send(capture(sent)) } returns Unit
        return WebRtcHandler(
            webRtcSessionFactory = factory,
            serverManager = serverManager,
            permissionManager = permissionManager,
            externalBusRepository = externalBus,
            audioManager = audioManager,
        )
    }

    private fun OutgoingExternalBusMessage.asJson(): JsonObject = frontendExternalBusJson.encodeToJsonElement<OutgoingExternalBusMessage>(this).jsonObject

    private fun OutgoingExternalBusMessage.errorCode(): String = asJson().getValue("error").jsonObject.getValue("code").jsonPrimitive.content

    private fun OutgoingExternalBusMessage.stoppedCode(): String = asJson().getValue("payload").jsonObject.getValue("code").jsonPrimitive.content

    private fun OutgoingExternalBusMessage.command(): String = asJson().getValue("command").jsonPrimitive.content

    @Test
    fun `Given a start When the session connects Then a mic-only session is created and a session id is replied`() = runTest {
        coEvery { permissionManager.checkMicrophonePermission() } returns true
        val handler = createHandler()

        handler.start(scope = backgroundScope, serverId = SERVER_ID, messageId = 5, streamType = WebRtcStreamType.MICROPHONE, entityId = "camera.front_door")
        runCurrent()

        assertEquals(1, factory.created.size)
        assertTrue(factory.lastSignalingClient is HaSignalingClient)
        assertEquals(MediaOptions(videoIn = false, audioIn = true, audioOut = true), factory.lastMediaOptions)
        assertEquals(false, factory.created.single().playbackEnabled)
        assertTrue(factory.created.single().started)

        factory.created.single().stateFlow.value = ConnectionState.Connected
        runCurrent()

        val result = sent.single().asJson()
        assertTrue(result.getValue("success").jsonPrimitive.boolean)
        assertTrue("session_id" in result.getValue("result").jsonObject)
    }

    @Test
    fun `Given a connected session When it later fails Then stopped with the failure code is sent, not a second result`() = runTest {
        coEvery { permissionManager.checkMicrophonePermission() } returns true
        val handler = createHandler()
        handler.start(scope = backgroundScope, serverId = SERVER_ID, messageId = 5, streamType = WebRtcStreamType.MICROPHONE, entityId = "camera.front_door")
        runCurrent()
        val session = factory.created.single()
        session.stateFlow.value = ConnectionState.Connected
        runCurrent()

        session.stateFlow.value = ConnectionState.Failed(SessionFailure.ConnectionLost)
        runCurrent()

        assertEquals(2, sent.size)
        assertTrue(sent[0].asJson().getValue("success").jsonPrimitive.boolean)
        assertEquals("webrtc/stream/stopped", sent[1].command())
        assertEquals("connection_failed", sent[1].stoppedCode())
        assertTrue(session.released)
    }

    @Test
    fun `Given a start When it fails before connecting Then a signaling_failed error result is sent`() = runTest {
        coEvery { permissionManager.checkMicrophonePermission() } returns true
        val handler = createHandler()
        handler.start(scope = backgroundScope, serverId = SERVER_ID, messageId = 5, streamType = WebRtcStreamType.MICROPHONE, entityId = "camera.front_door")
        runCurrent()

        factory.created.single().stateFlow.value =
            ConnectionState.Failed(SessionFailure.Signaling("nope"))
        runCurrent()

        val result = sent.single().asJson()
        assertFalse(result.getValue("success").jsonPrimitive.boolean)
        // The HA code is not a frontend error code, so the fixed "signaling_failed" is sent instead.
        assertEquals("signaling_failed", sent.single().errorCode())
    }

    @Test
    fun `Given a session that never connects When the connect timeout elapses Then a timeout error is replied and the session released`() = runTest {
        coEvery { permissionManager.checkMicrophonePermission() } returns true
        val handler = createHandler()
        handler.start(scope = backgroundScope, serverId = SERVER_ID, messageId = 5, streamType = WebRtcStreamType.MICROPHONE, entityId = "camera.front_door")
        runCurrent()
        // Session created but still stuck before connecting: no reply yet.
        assertEquals(1, factory.created.size)
        assertTrue(sent.isEmpty())

        advanceTimeBy(26.seconds)
        runCurrent()

        assertEquals("timeout", sent.single().errorCode())
        assertTrue(factory.created.single().released)
    }

    @Test
    fun `Given microphone permission denied When starting Then an error is replied and no session is created`() = runTest {
        coEvery { permissionManager.checkMicrophonePermission() } returns false
        val handler = createHandler()

        handler.start(scope = backgroundScope, serverId = SERVER_ID, messageId = 5, streamType = WebRtcStreamType.MICROPHONE, entityId = "camera.front_door")
        runCurrent()

        assertEquals(0, factory.created.size)
        assertEquals("microphone_denied", sent.single().errorCode())
    }

    @Test
    fun `Given no API for the server When starting Then a server_unavailable error is replied and no session is created`() = runTest {
        coEvery { permissionManager.checkMicrophonePermission() } returns true
        coEvery { serverManager.webSocketRepository(SERVER_ID) } throws IllegalStateException("no server")
        val handler = createHandler()

        handler.start(scope = backgroundScope, serverId = SERVER_ID, messageId = 5, streamType = WebRtcStreamType.MICROPHONE, entityId = "camera.front_door")
        runCurrent()

        assertEquals(0, factory.created.size)
        assertEquals("server_unavailable", sent.single().errorCode())
    }

    @Test
    fun `Given an unsupported stream type When starting Then an error is replied and no session is created`() = runTest {
        val handler = createHandler()

        handler.start(
            scope = backgroundScope,
            serverId = SERVER_ID,
            messageId = 5,
            streamType = WebRtcStreamType.UNKNOWN,
            entityId = "camera.front_door",
        )
        runCurrent()

        assertEquals(0, factory.created.size)
        assertEquals("unsupported_stream_type", sent.single().errorCode())
    }

    @Test
    fun `Given an unknown session When stopping Then nothing happens`() = runTest {
        val handler = createHandler()
        handler.stop("does-not-exist")
    }

    @Test
    fun `Given two sessions When both stop Then communication mode is entered once and restored once`() = runTest {
        coEvery { permissionManager.checkMicrophonePermission() } returns true
        coEvery { audioManager.mode } returns AudioManager.MODE_NORMAL
        val modes = mutableListOf<Int>()
        every { audioManager.mode = capture(modes) } returns Unit
        val handler = createHandler()

        handler.start(scope = backgroundScope, serverId = SERVER_ID, messageId = 1, streamType = WebRtcStreamType.MICROPHONE, entityId = "camera.a")
        runCurrent()
        handler.start(scope = backgroundScope, serverId = SERVER_ID, messageId = 2, streamType = WebRtcStreamType.MICROPHONE, entityId = "camera.b")
        runCurrent()
        val ids = factory.created
        ids[0].stateFlow.value = ConnectionState.Connected
        ids[1].stateFlow.value = ConnectionState.Connected
        runCurrent()

        // Entered communication mode once (on the first session), not again for the second
        assertEquals(1, modes.count { it == AudioManager.MODE_IN_COMMUNICATION })

        handler.stop(factorySessionId(0))
        handler.stop(factorySessionId(1))
        runCurrent()
        // Restored to the previous mode once, only after the last session stopped
        assertEquals(1, modes.count { it == AudioManager.MODE_NORMAL })
    }

    @Test
    fun `Given a connected session When the frontend stops it Then it is released silently without a stopped notification`() = runTest {
        coEvery { permissionManager.checkMicrophonePermission() } returns true
        val handler = createHandler()
        handler.start(scope = backgroundScope, serverId = SERVER_ID, messageId = 5, streamType = WebRtcStreamType.MICROPHONE, entityId = "camera.front_door")
        runCurrent()
        factory.created.single().stateFlow.value = ConnectionState.Connected
        runCurrent()
        val sessionId = factorySessionId(0)
        sent.clear()

        handler.stop(sessionId)
        runCurrent()

        // A frontend-requested stop is silent: stopped is only for ends the frontend did not ask for.
        assertTrue(sent.isEmpty())
        assertTrue(factory.created.single().released)
    }

    @Test
    fun `Given active sessions When stopAll notifies the frontend Then an interrupted stopped is sent for each`() = runTest {
        coEvery { permissionManager.checkMicrophonePermission() } returns true
        val handler = createHandler()
        handler.start(scope = backgroundScope, serverId = SERVER_ID, messageId = 1, streamType = WebRtcStreamType.MICROPHONE, entityId = "camera.a")
        runCurrent()
        handler.start(scope = backgroundScope, serverId = SERVER_ID, messageId = 2, streamType = WebRtcStreamType.MICROPHONE, entityId = "camera.b")
        runCurrent()
        factory.created.forEach { it.stateFlow.value = ConnectionState.Connected }
        runCurrent()
        sent.clear()

        handler.stopAll(notifyFrontend = true)
        runCurrent()

        assertEquals(2, sent.size)
        assertTrue(sent.all { it.command() == "webrtc/stream/stopped" })
        assertTrue(sent.all { it.stoppedCode() == "interrupted" })
        assertTrue(factory.created.all { it.released })
    }

    @Test
    fun `Given active sessions When stopAll without notifying Then no stopped is sent`() = runTest {
        coEvery { permissionManager.checkMicrophonePermission() } returns true
        val handler = createHandler()
        handler.start(scope = backgroundScope, serverId = SERVER_ID, messageId = 1, streamType = WebRtcStreamType.MICROPHONE, entityId = "camera.a")
        runCurrent()
        factory.created.single().stateFlow.value = ConnectionState.Connected
        runCurrent()
        sent.clear()

        handler.stopAll()
        runCurrent()

        assertTrue(sent.isEmpty())
        assertTrue(factory.created.single().released)
    }

    @Test
    fun `Given a start awaiting the permission When stopAll runs Then the request is replied interrupted and nothing is opened`() = runTest {
        val permissionGate = CompletableDeferred<Boolean>()
        coEvery { permissionManager.checkMicrophonePermission() } coAnswers { permissionGate.await() }
        val modes = mutableListOf<Int>()
        every { audioManager.mode = capture(modes) } returns Unit
        val handler = createHandler()
        // The start suspends in the permission request, so it runs in the background
        backgroundScope.launch {
            handler.start(scope = backgroundScope, serverId = SERVER_ID, messageId = 5, streamType = WebRtcStreamType.MICROPHONE, entityId = "camera.front_door")
        }
        runCurrent()

        // The frontend went away while the permission dialog was up
        handler.stopAll()
        permissionGate.complete(true)
        runCurrent()

        assertEquals(0, factory.created.size)
        assertTrue(modes.isEmpty(), "The audio mode must not be touched for an abandoned start")
        assertEquals("interrupted", sent.single().errorCode())
    }

    @Test
    fun `Given a start creating its session When stopAll runs Then the session is released and the request replied interrupted`() = runTest {
        coEvery { permissionManager.checkMicrophonePermission() } returns true
        coEvery { audioManager.mode } returns AudioManager.MODE_NORMAL
        val modes = mutableListOf<Int>()
        every { audioManager.mode = capture(modes) } returns Unit
        factory.createGate = CompletableDeferred()
        val handler = createHandler()
        // The start suspends in the session creation, so it runs in the background
        backgroundScope.launch {
            handler.start(scope = backgroundScope, serverId = SERVER_ID, messageId = 5, streamType = WebRtcStreamType.MICROPHONE, entityId = "camera.front_door")
        }
        runCurrent()

        handler.stopAll()
        factory.createGate?.complete(Unit)
        runCurrent()

        val session = factory.created.single()
        assertFalse(session.started)
        assertTrue(session.released)
        assertEquals(listOf(AudioManager.MODE_IN_COMMUNICATION, AudioManager.MODE_NORMAL), modes)
        assertEquals("interrupted", sent.single().errorCode())
    }

    @Test
    fun `Given a start creating its session When its scope is cancelled Then the audio mode is restored`() = runTest {
        coEvery { permissionManager.checkMicrophonePermission() } returns true
        coEvery { audioManager.mode } returns AudioManager.MODE_NORMAL
        val modes = mutableListOf<Int>()
        every { audioManager.mode = capture(modes) } returns Unit
        factory.createGate = CompletableDeferred()
        val handler = createHandler()
        val startJob = backgroundScope.launch {
            handler.start(scope = backgroundScope, serverId = SERVER_ID, messageId = 5, streamType = WebRtcStreamType.MICROPHONE, entityId = "camera.front_door")
        }
        runCurrent()
        assertEquals(listOf(AudioManager.MODE_IN_COMMUNICATION), modes)

        // The ViewModel is cleared while the session factory is still initialising
        startJob.cancel()
        runCurrent()

        assertEquals(listOf(AudioManager.MODE_IN_COMMUNICATION, AudioManager.MODE_NORMAL), modes)
        assertTrue(factory.created.isEmpty())
        assertTrue(sent.isEmpty())
    }

    @Test
    fun `Given a session still connecting When stopAll notifies the frontend Then the start request fails instead of a stopped notification`() = runTest {
        coEvery { permissionManager.checkMicrophonePermission() } returns true
        val handler = createHandler()
        handler.start(scope = backgroundScope, serverId = SERVER_ID, messageId = 5, streamType = WebRtcStreamType.MICROPHONE, entityId = "camera.front_door")
        runCurrent()
        assertTrue(sent.isEmpty())

        handler.stopAll(notifyFrontend = true)
        runCurrent()

        // The frontend still awaits the reply to its start, so that is where the interruption goes
        val reply = sent.single().asJson()
        assertEquals("result", reply.getValue("type").jsonPrimitive.content)
        assertEquals("interrupted", sent.single().errorCode())
        assertTrue(factory.created.single().released)
    }

    // Helper: the handler generates session ids internally; recover the id from the result messages
    // that carry one.
    private fun factorySessionId(index: Int): String {
        val sessionIds = sent.mapNotNull {
            it.asJson()["result"]?.jsonObject?.get("session_id")?.jsonPrimitive?.content
        }
        return sessionIds[index]
    }
}
