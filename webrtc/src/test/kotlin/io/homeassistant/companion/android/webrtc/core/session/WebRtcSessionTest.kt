package io.homeassistant.companion.android.webrtc.core.session

import app.cash.turbine.test
import io.homeassistant.companion.android.webrtc.core.ConnectionState
import io.homeassistant.companion.android.webrtc.core.MediaOptions
import io.homeassistant.companion.android.webrtc.core.MicState
import io.homeassistant.companion.android.webrtc.core.RtcStatsSnapshot
import io.homeassistant.companion.android.webrtc.core.SessionFailure
import io.homeassistant.companion.android.webrtc.core.WebRtcSession
import io.homeassistant.companion.android.webrtc.core.signaling.IceCandidateInit
import io.homeassistant.companion.android.webrtc.core.signaling.SignalingEvent
import io.homeassistant.companion.android.webrtc.core.signaling.SignalingResult
import io.homeassistant.companion.android.webrtc.core.signaling.WebRtcSessionId
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import livekit.org.webrtc.EglBase
import livekit.org.webrtc.VideoSink
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private val SESSION_ID = WebRtcSessionId("01JAYSESSION")

class WebRtcSessionTest {

    private val signaling = FakeSignalingClient()
    private val factory = FakePeerConnectionControllerFactory()

    // A non-native EGL context so video sessions produce a non-null videoIn handle.
    private val fakeEglContext = object : EglBase.Context {
        override fun getNativeEglContext(): Long = 0L
    }

    private fun TestScope.createSession(
        mediaOptions: MediaOptions = MediaOptions(),
        eglContext: EglBase.Context = fakeEglContext,
    ) = WebRtcSessionImpl(
        eglContext = eglContext,
        signalingClient = signaling,
        controllerFactory = factory,
        mediaOptions = mediaOptions,
        dispatcher = StandardTestDispatcher(testScheduler),
    )

    private suspend fun TestScope.startConnectedSession(session: WebRtcSession) {
        session.start()
        advanceUntilIdle()
        signaling.currentSession.trySend(SignalingEvent.Session(SESSION_ID))
        signaling.currentSession.trySend(SignalingEvent.Answer("v=0 answer"))
        advanceUntilIdle()
        factory.lastController.emit(PeerConnectionEvent.ConnectionStateChanged(RtcConnectionState.CONNECTED))
        advanceUntilIdle()
    }

    @Test
    fun `Given a start When the offer is answered Then it stays Connecting until the peer connects`() = runTest {
        val session = createSession()

        session.start()
        assertEquals(ConnectionState.Idle, session.state.value)
        advanceUntilIdle()

        assertEquals(ConnectionState.Connecting, session.state.value)
        assertEquals(1, signaling.openSessionCount)
        assertEquals("v=0 fake-offer-0", signaling.lastOfferSdp)

        signaling.currentSession.trySend(SignalingEvent.Session(SESSION_ID))
        signaling.currentSession.trySend(SignalingEvent.Answer("v=0 answer"))
        advanceUntilIdle()

        assertEquals(listOf("v=0 answer"), factory.lastController.appliedAnswers)
        // The answer alone does not mean connected; the state stays Connecting until the peer reports it.
        assertEquals(ConnectionState.Connecting, session.state.value)

        factory.lastController.emit(PeerConnectionEvent.ConnectionStateChanged(RtcConnectionState.CONNECTED))
        advanceUntilIdle()

        assertEquals(ConnectionState.Connected, session.state.value)
    }

    @Test
    fun `Given trickle ICE When candidates arrive before the answer Then they are applied after it`() = runTest {
        val session = createSession()
        session.start()
        advanceUntilIdle()

        val earlyCandidate = IceCandidateInit(candidate = "candidate:early")
        signaling.currentSession.trySend(SignalingEvent.Candidate(earlyCandidate))
        advanceUntilIdle()
        assertTrue(factory.lastController.remoteCandidates.isEmpty())

        signaling.currentSession.trySend(SignalingEvent.Session(SESSION_ID))
        signaling.currentSession.trySend(SignalingEvent.Answer("v=0 answer"))
        advanceUntilIdle()
        assertEquals(listOf(earlyCandidate), factory.lastController.remoteCandidates)

        val lateCandidate = IceCandidateInit(candidate = "candidate:late")
        signaling.currentSession.trySend(SignalingEvent.Candidate(lateCandidate))
        advanceUntilIdle()
        assertEquals(listOf(earlyCandidate, lateCandidate), factory.lastController.remoteCandidates)
    }

    @Test
    fun `Given trickle ICE When local candidates are found before the session id Then they are sent after it`() = runTest {
        val session = createSession()
        session.start()
        advanceUntilIdle()

        val earlyCandidate = IceCandidateInit(candidate = "candidate:local-early")
        factory.lastController.emit(PeerConnectionEvent.LocalCandidate(earlyCandidate))
        advanceUntilIdle()
        assertTrue(signaling.sentCandidates.isEmpty())

        signaling.currentSession.trySend(SignalingEvent.Session(SESSION_ID))
        advanceUntilIdle()
        assertEquals(listOf(SESSION_ID to earlyCandidate), signaling.sentCandidates)

        val lateCandidate = IceCandidateInit(candidate = "candidate:local-late")
        factory.lastController.emit(PeerConnectionEvent.LocalCandidate(lateCandidate))
        advanceUntilIdle()
        assertEquals(
            listOf(SESSION_ID to earlyCandidate, SESSION_ID to lateCandidate),
            signaling.sentCandidates,
        )
    }

    @Test
    fun `Given an error event When negotiating Then the session fails and releases everything`() = runTest {
        val session = createSession()
        session.start()
        advanceUntilIdle()

        signaling.currentSession.trySend(
            SignalingEvent.Error(code = "webrtc_offer_failed", message = "Camera does not support WebRTC"),
        )
        advanceUntilIdle()

        assertEquals(
            ConnectionState.Failed(SessionFailure.Signaling("webrtc_offer_failed", "Camera does not support WebRTC")),
            session.state.value,
        )
        assertTrue(factory.lastController.disposed)
        assertEquals(0, signaling.activeSessionCount)
    }

    @Test
    fun `Given a failing client config When starting Then the session fails without opening a session`() = runTest {
        signaling.clientConfigFailure = SignalingResult.Failure(code = "webrtc_get_client_config_failed")
        val session = createSession()

        session.start()
        advanceUntilIdle()

        assertEquals(
            ConnectionState.Failed(SessionFailure.Signaling("webrtc_get_client_config_failed", null)),
            session.state.value,
        )
        assertEquals(0, signaling.openSessionCount)
    }

    @Test
    fun `Given an offer creation failure When starting Then the session fails with an internal error`() = runTest {
        val session = createSession()
        session.start()
        advanceUntilIdle()
        // The first negotiation is waiting for signaling events; force the next one to fail fast
        // by failing the whole factory, then trigger a reconnection
        factory.createException = IllegalStateException("no offer")
        factory.lastController.emit(PeerConnectionEvent.ConnectionStateChanged(RtcConnectionState.FAILED))
        advanceUntilIdle()

        assertEquals(ConnectionState.Failed(SessionFailure.Internal("no offer")), session.state.value)
        assertEquals(0, signaling.activeSessionCount)
    }

    @Test
    fun `Given a connected session When stopping Then everything is released`() = runTest {
        val session = createSession()
        startConnectedSession(session)
        assertEquals(ConnectionState.Connected, session.state.value)

        session.stop()
        advanceUntilIdle()

        assertEquals(ConnectionState.Idle, session.state.value)
        assertTrue(factory.lastController.disposed)
        assertEquals(0, signaling.activeSessionCount)
    }

    @Test
    fun `Given a stopped session When starting again Then a new session starts after full cleanup`() = runTest {
        val session = createSession()
        startConnectedSession(session)

        session.stop()
        // Restart immediately, without letting the cancelled session finish its cleanup first
        session.start()
        advanceUntilIdle()

        assertEquals(2, factory.controllers.size)
        assertTrue(factory.controllers.first().disposed)
        assertFalse(factory.lastController.disposed)
        assertEquals(2, signaling.openSessionCount)
        assertEquals(1, signaling.activeSessionCount)
    }

    @Test
    fun `Given repeated disconnections When recovering before the grace period Then no stale timer fires`() = runTest {
        val session = createSession()
        startConnectedSession(session)

        factory.lastController.emit(PeerConnectionEvent.ConnectionStateChanged(RtcConnectionState.DISCONNECTED))
        advanceTimeBy(3.seconds)
        factory.lastController.emit(PeerConnectionEvent.ConnectionStateChanged(RtcConnectionState.DISCONNECTED))
        advanceTimeBy(3.seconds)
        // The first timer would fire now if it were not cancelled by the second DISCONNECTED
        factory.lastController.emit(PeerConnectionEvent.ConnectionStateChanged(RtcConnectionState.CONNECTED))
        advanceUntilIdle()

        assertEquals(ConnectionState.Connected, session.state.value)
        assertEquals(1, signaling.openSessionCount)
        assertFalse(factory.lastController.disposed)
    }

    @Test
    fun `Given a lost connection When the grace period expires Then the session renegotiates`() = runTest {
        val session = createSession()
        startConnectedSession(session)

        factory.lastController.emit(PeerConnectionEvent.ConnectionStateChanged(RtcConnectionState.DISCONNECTED))
        advanceTimeBy(1.seconds)
        assertEquals(ConnectionState.Reconnecting, session.state.value)
        assertEquals(1, signaling.openSessionCount)

        advanceUntilIdle()

        assertEquals(2, signaling.openSessionCount)
        assertEquals(2, factory.controllers.size)
        assertTrue(factory.controllers.first().disposed)
    }

    @Test
    fun `Given a transient disconnection When it recovers within the grace period Then nothing is renegotiated`() = runTest {
        val session = createSession()
        startConnectedSession(session)

        factory.lastController.emit(PeerConnectionEvent.ConnectionStateChanged(RtcConnectionState.DISCONNECTED))
        advanceTimeBy(2.seconds)
        assertEquals(ConnectionState.Reconnecting, session.state.value)

        factory.lastController.emit(PeerConnectionEvent.ConnectionStateChanged(RtcConnectionState.CONNECTED))
        advanceUntilIdle()

        assertEquals(ConnectionState.Connected, session.state.value)
        assertEquals(1, signaling.openSessionCount)
        assertFalse(factory.lastController.disposed)
    }

    @Test
    fun `Given repeated connection failures When reconnecting Then the session gives up after the cap`() = runTest {
        val session = createSession()
        session.start()
        advanceUntilIdle()

        // Initial negotiation + 3 reconnection attempts all fail
        repeat(4) {
            factory.lastController.emit(PeerConnectionEvent.ConnectionStateChanged(RtcConnectionState.FAILED))
            advanceUntilIdle()
        }

        assertEquals(ConnectionState.Failed(SessionFailure.ConnectionLost), session.state.value)
        assertEquals(4, factory.controllers.size)
        assertTrue(factory.controllers.all { it.disposed })
        assertEquals(0, signaling.activeSessionCount)
    }

    @Test
    fun `Given a connected session Then the signaling subscription is closed and a later close does not renegotiate`() = runTest {
        val session = createSession()
        startConnectedSession(session)

        // Once connected, the signaling subscription is released; the media flows on its own transport,
        // so a WebSocket reconnect cannot resubscribe it and re-offer onto the already-negotiated peer.
        assertEquals(0, signaling.activeSessionCount)

        signaling.currentSession.close()
        advanceUntilIdle()

        assertEquals(ConnectionState.Connected, session.state.value)
        assertEquals(1, signaling.openSessionCount)
    }

    @Test
    fun `Given the signaling subscription closes before connecting Then the session renegotiates`() = runTest {
        val session = createSession()
        session.start()
        advanceUntilIdle()
        assertEquals(ConnectionState.Connecting, session.state.value)

        // A close before the connection is established is a real server drop: the session renegotiates.
        signaling.currentSession.close()
        advanceUntilIdle()

        assertEquals(2, signaling.openSessionCount)
    }

    @Test
    fun `Given the mic enabled before start When the session connects Then the mic goes live`() = runTest {
        val session = createSession(MediaOptions(audioOut = true))
        session.audioOut!!.setEnabled(true)
        assertEquals(MicState.Unavailable, session.audioOut!!.state.value)

        startConnectedSession(session)

        assertEquals(MicState.Live, session.audioOut!!.state.value)
        assertEquals(true, factory.lastController.microphoneEnabled)
    }

    @Test
    fun `Given a live mic When disabling it Then the track is disabled and the state is Off`() = runTest {
        val session = createSession(MediaOptions(audioOut = true))
        startConnectedSession(session)
        assertEquals(MicState.Live, session.audioOut!!.state.value)

        session.audioOut!!.setEnabled(false)

        assertEquals(MicState.Off, session.audioOut!!.state.value)
        assertEquals(false, factory.lastController.microphoneEnabled)
    }

    @Test
    fun `Given a live mic When the session reconnects Then the mic is enabled on the new connection`() = runTest {
        val session = createSession(MediaOptions(audioOut = true))
        startConnectedSession(session)

        factory.lastController.emit(PeerConnectionEvent.ConnectionStateChanged(RtcConnectionState.FAILED))
        advanceUntilIdle()

        assertEquals(2, factory.controllers.size)
        assertEquals(true, factory.lastController.microphoneEnabled)
    }

    @Test
    fun `Given a live mic When the session stops Then the mic state is Off`() = runTest {
        val session = createSession(MediaOptions(audioOut = true))
        startConnectedSession(session)
        assertEquals(MicState.Live, session.audioOut!!.state.value)

        session.stop()
        advanceUntilIdle()

        assertEquals(MicState.Off, session.audioOut!!.state.value)
    }

    @Test
    fun `Given attached sinks and muted audio When the session connects Then they are applied`() = runTest {
        val session = createSession()
        val sink = VideoSink { }
        session.videoIn!!.attachSink(sink)
        session.audioIn!!.setPlaybackEnabled(false)

        startConnectedSession(session)

        assertTrue(sink in factory.lastController.sinks)
        assertEquals(false, factory.lastController.remoteAudioEnabled)

        session.videoIn!!.detachSink(sink)
        assertFalse(sink in factory.lastController.sinks)
    }

    @Test
    fun `Given a released session When starting again Then nothing happens`() = runTest {
        val session = createSession()
        startConnectedSession(session)

        session.release()
        advanceUntilIdle()
        assertEquals(ConnectionState.Idle, session.state.value)
        assertTrue(factory.lastController.disposed)

        session.start()
        advanceUntilIdle()

        assertEquals(ConnectionState.Idle, session.state.value)
        assertEquals(1, signaling.openSessionCount)
    }

    @Test
    fun `Given a rejected local candidate When streaming Then the session keeps going`() = runTest {
        signaling.sendCandidateResult = false
        val session = createSession()
        startConnectedSession(session)

        factory.lastController.emit(
            PeerConnectionEvent.LocalCandidate(IceCandidateInit(candidate = "candidate:rejected")),
        )
        advanceUntilIdle()

        assertEquals(ConnectionState.Connected, session.state.value)
        assertNull(session.state.value as? ConnectionState.Failed)
    }

    @Test
    fun `Given custom media options When starting Then the controller negotiates them`() = runTest {
        val options = MediaOptions(videoIn = false, audioIn = true, audioOut = true)
        val session = createSession(options)

        session.start()
        advanceUntilIdle()

        assertEquals(options, factory.lastController.mediaOptions)
    }

    @Test
    fun `Given send audio negotiated When the session connects Then the microphone goes live`() = runTest {
        val session = createSession(MediaOptions(audioOut = true))

        startConnectedSession(session)

        assertEquals(true, factory.lastController.microphoneEnabled)
        assertEquals(MicState.Live, session.audioOut!!.state.value)
    }

    @Test
    fun `Given talk-only options Then only audioOut is present`() = runTest {
        val session = createSession(MediaOptions(videoIn = false, audioIn = false, audioOut = true))
        assertNull(session.videoIn)
        assertNull(session.audioIn)
        assertNotNull(session.audioOut)
    }

    @Test
    fun `Given listen-only options Then only audioIn is present`() = runTest {
        val session = createSession(MediaOptions(videoIn = false, audioIn = true, audioOut = false))
        assertNull(session.videoIn)
        assertNotNull(session.audioIn)
        assertNull(session.audioOut)
    }

    @Test
    fun `Given intercom options Then both audio handles are present and video is absent`() = runTest {
        val session = createSession(MediaOptions(videoIn = false, audioIn = true, audioOut = true))
        assertNull(session.videoIn)
        assertNotNull(session.audioIn)
        assertNotNull(session.audioOut)
    }

    @Test
    fun `Given video-in options Then videoIn is present and exposes the EGL context`() = runTest {
        val session = createSession(MediaOptions(videoIn = true, audioIn = false, audioOut = false))
        assertNotNull(session.videoIn)
        assertSame(fakeEglContext, session.videoIn?.eglContext)
        assertNull(session.audioIn)
        assertNull(session.audioOut)
    }

    @Test
    fun `Given a session When it reconnects Then the videoIn handle is stable`() = runTest {
        val session = createSession(MediaOptions(videoIn = true))
        val handleBefore = session.videoIn
        startConnectedSession(session)

        factory.lastController.emit(PeerConnectionEvent.ConnectionStateChanged(RtcConnectionState.FAILED))
        advanceUntilIdle()

        assertSame(handleBefore, session.videoIn)
    }

    @Test
    fun `Given a released session When toggling the mic Then it does not throw`() = runTest {
        val session = createSession(MediaOptions(audioOut = true))
        startConnectedSession(session)

        session.release()
        advanceUntilIdle()

        session.audioOut!!.setEnabled(true)
    }

    @Test
    fun `Given listen-only When the peer connects Then state is Connected`() = runTest {
        val session = createSession(MediaOptions(videoIn = false, audioIn = true, audioOut = false))

        startConnectedSession(session)

        assertEquals(ConnectionState.Connected, session.state.value)
    }

    @Test
    fun `Given no requested streams When starting Then no session is negotiated`() = runTest {
        val session = createSession(MediaOptions(videoIn = false, audioIn = false, audioOut = false))

        session.start()
        advanceUntilIdle()

        assertEquals(ConnectionState.Idle, session.state.value)
        assertEquals(0, signaling.openSessionCount)
    }

    @Test
    fun `Given no active session When observing stats Then empty snapshots are emitted`() = runTest {
        val session = createSession()

        session.observeStats(1.seconds).test {
            assertEquals(RtcStatsSnapshot.EMPTY, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Given consecutive snapshots When observing stats Then each poll is enriched against the previous`() = runTest {
        val session = createSession()
        startConnectedSession(session)
        val snapshots = listOf(
            RtcStatsSnapshot(timestamp = Instant.fromEpochSeconds(0), inboundVideo = RtcStatsSnapshot.RtpStreamStats(bytes = 0)),
            RtcStatsSnapshot(
                timestamp = Instant.fromEpochSeconds(1),
                inboundVideo = RtcStatsSnapshot.RtpStreamStats(bytes = 25_000),
            ),
        )
        var index = 0
        factory.lastController.statsProvider = { snapshots[minOf(index++, snapshots.lastIndex)] }

        session.observeStats(1.seconds).test {
            // First poll has no predecessor, so it is emitted as-is with no derived rate.
            assertEquals(0, awaitItem().inboundVideo?.kbps)
            advanceTimeBy(1.seconds)
            runCurrent()
            // The second poll is enriched against the first: the session threads previous -> current.
            // The derivation math itself is covered by RtcStatsSnapshotTest.
            assertTrue((awaitItem().inboundVideo?.kbps ?: 0) > 0)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Given a terminal error then a closed subscription When negotiating Then the session fails without retrying`() = runTest {
        val session = createSession()
        session.start()
        advanceUntilIdle()

        // The error and the normal close arrive in the same batch: the terminal error must win over
        // the completion, otherwise the session would treat the rejection as a clean end and retry.
        signaling.currentSession.trySend(SignalingEvent.Error(code = "webrtc_offer_failed", message = "boom"))
        signaling.currentSession.close()
        advanceUntilIdle()

        assertEquals(
            ConnectionState.Failed(SessionFailure.Signaling("webrtc_offer_failed", "boom")),
            session.state.value,
        )
        assertEquals(1, signaling.openSessionCount)
    }

    @Test
    fun `Given a connected peer that fails When backing off before reconnecting Then the state is Reconnecting`() = runTest {
        val session = createSession()
        startConnectedSession(session)
        assertEquals(ConnectionState.Connected, session.state.value)

        factory.lastController.emit(PeerConnectionEvent.ConnectionStateChanged(RtcConnectionState.FAILED))
        // Process the failure and the pre-backoff state change, but do not advance past the backoff.
        runCurrent()

        // During the backoff the peer connection is already disposed, so the state must not read Connected.
        assertEquals(ConnectionState.Reconnecting, session.state.value)

        advanceUntilIdle()
        assertEquals(2, signaling.openSessionCount)
    }

    @Test
    fun `Given a slow disposal When stopping Then stop suspends until the controller is disposed`() = runTest {
        val session = createSession()
        startConnectedSession(session)
        val controller = factory.lastController
        val disposeGate = CompletableDeferred<Unit>()
        controller.disposeGate = disposeGate

        var stopReturned = false
        val stopping = launch {
            session.stop()
            stopReturned = true
        }
        advanceUntilIdle()

        // stop() must not return while the controller is still disposing: a later start() relies on
        // this to never run a new session over a not-yet-disposed one.
        assertFalse(stopReturned)
        assertFalse(controller.disposed)

        disposeGate.complete(Unit)
        advanceUntilIdle()
        stopping.join()

        assertTrue(stopReturned)
        assertTrue(controller.disposed)
    }
}
