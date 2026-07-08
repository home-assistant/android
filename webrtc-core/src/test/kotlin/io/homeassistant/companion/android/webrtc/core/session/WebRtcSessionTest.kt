package io.homeassistant.companion.android.webrtc.core.session

import io.homeassistant.companion.android.webrtc.core.MicState
import io.homeassistant.companion.android.webrtc.core.PlayerFailure
import io.homeassistant.companion.android.webrtc.core.PlayerState
import io.homeassistant.companion.android.webrtc.core.signaling.IceCandidateInit
import io.homeassistant.companion.android.webrtc.core.signaling.SignalingEvent
import io.homeassistant.companion.android.webrtc.core.signaling.SignalingException
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import livekit.org.webrtc.VideoSink
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private const val ENTITY_ID = "camera.front_door"
private const val SESSION_ID = "01JAYSESSION"

class WebRtcSessionTest {

    private val signaling = FakeSignalingClient()
    private val factory = FakePeerConnectionControllerFactory()
    private val audio = FakeAudioController()

    private fun TestScope.createSession() = WebRtcSession(
        ENTITY_ID,
        signaling,
        factory,
        audio,
        StandardTestDispatcher(testScheduler),
    )

    private fun TestScope.startConnectedSession(session: WebRtcSession) {
        session.start()
        advanceUntilIdle()
        signaling.currentSession.trySend(SignalingEvent.Session(SESSION_ID))
        signaling.currentSession.trySend(SignalingEvent.Answer("v=0 answer"))
        advanceUntilIdle()
        factory.lastController.emit(PeerConnectionEvent.ConnectionStateChanged(RtcConnectionState.CONNECTED))
        advanceUntilIdle()
    }

    @Test
    fun `Given a camera When starting Then the offer is sent and the session reaches Playing`() = runTest {
        val session = createSession()

        session.start()
        assertEquals(PlayerState.Idle, session.state.value)
        advanceUntilIdle()

        assertEquals(PlayerState.Connecting, session.state.value)
        assertEquals(1, signaling.openSessionCount)
        assertEquals("v=0 fake-offer-0", signaling.lastOfferSdp)

        signaling.currentSession.trySend(SignalingEvent.Session(SESSION_ID))
        signaling.currentSession.trySend(SignalingEvent.Answer("v=0 answer"))
        advanceUntilIdle()

        assertEquals(listOf("v=0 answer"), factory.lastController.appliedAnswers)
        assertEquals(PlayerState.Buffering, session.state.value)

        factory.lastController.emit(PeerConnectionEvent.ConnectionStateChanged(RtcConnectionState.CONNECTED))
        advanceUntilIdle()

        assertEquals(PlayerState.Playing, session.state.value)
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
            PlayerState.Failed(PlayerFailure.Signaling("webrtc_offer_failed", "Camera does not support WebRTC")),
            session.state.value,
        )
        assertTrue(factory.lastController.disposed)
        assertEquals(0, signaling.activeSessionCount)
    }

    @Test
    fun `Given a failing client config When starting Then the session fails without opening a session`() = runTest {
        signaling.clientConfigException = SignalingException(code = "webrtc_get_client_config_failed")
        val session = createSession()

        session.start()
        advanceUntilIdle()

        assertEquals(
            PlayerState.Failed(PlayerFailure.Signaling("webrtc_get_client_config_failed", null)),
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

        assertEquals(PlayerState.Failed(PlayerFailure.Internal("no offer")), session.state.value)
        assertEquals(0, signaling.activeSessionCount)
    }

    @Test
    fun `Given a connected session When stopping Then everything is released`() = runTest {
        val session = createSession()
        startConnectedSession(session)
        assertEquals(PlayerState.Playing, session.state.value)

        session.stop()
        advanceUntilIdle()

        assertEquals(PlayerState.Idle, session.state.value)
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

        assertEquals(PlayerState.Playing, session.state.value)
        assertEquals(1, signaling.openSessionCount)
        assertFalse(factory.lastController.disposed)
    }

    @Test
    fun `Given a lost connection When the grace period expires Then the session renegotiates`() = runTest {
        val session = createSession()
        startConnectedSession(session)

        factory.lastController.emit(PeerConnectionEvent.ConnectionStateChanged(RtcConnectionState.DISCONNECTED))
        advanceTimeBy(1.seconds)
        assertEquals(PlayerState.Buffering, session.state.value)
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
        assertEquals(PlayerState.Buffering, session.state.value)

        factory.lastController.emit(PeerConnectionEvent.ConnectionStateChanged(RtcConnectionState.CONNECTED))
        advanceUntilIdle()

        assertEquals(PlayerState.Playing, session.state.value)
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

        assertEquals(PlayerState.Failed(PlayerFailure.ConnectionLost), session.state.value)
        assertEquals(4, factory.controllers.size)
        assertTrue(factory.controllers.all { it.disposed })
        assertEquals(0, signaling.activeSessionCount)
    }

    @Test
    fun `Given a server closed subscription When streaming Then the session renegotiates`() = runTest {
        val session = createSession()
        startConnectedSession(session)

        signaling.currentSession.close()
        advanceUntilIdle()

        assertEquals(2, signaling.openSessionCount)
        assertEquals(PlayerState.Buffering, session.state.value)
    }

    @Test
    fun `Given a mic request before start When the session connects Then the mic goes live`() = runTest {
        val session = createSession()
        session.setMicEnabled(true)
        assertEquals(MicState.Unavailable, session.micState.value)

        startConnectedSession(session)

        assertEquals(MicState.Live, session.micState.value)
        assertEquals(true, factory.lastController.microphoneEnabled)
    }

    @Test
    fun `Given a live mic When disabling it Then the track is disabled and the state is Off`() = runTest {
        val session = createSession()
        startConnectedSession(session)
        session.setMicEnabled(true)
        assertEquals(MicState.Live, session.micState.value)

        session.setMicEnabled(false)

        assertEquals(MicState.Off, session.micState.value)
        assertEquals(false, factory.lastController.microphoneEnabled)
    }

    @Test
    fun `Given a live mic When the session reconnects Then the mic is enabled on the new connection`() = runTest {
        val session = createSession()
        startConnectedSession(session)
        session.setMicEnabled(true)

        factory.lastController.emit(PeerConnectionEvent.ConnectionStateChanged(RtcConnectionState.FAILED))
        advanceUntilIdle()

        assertEquals(2, factory.controllers.size)
        assertEquals(true, factory.lastController.microphoneEnabled)
    }

    @Test
    fun `Given a live mic When the session stops Then the mic state is Off`() = runTest {
        val session = createSession()
        startConnectedSession(session)
        session.setMicEnabled(true)

        session.stop()
        advanceUntilIdle()

        assertEquals(MicState.Off, session.micState.value)
    }

    @Test
    fun `Given a mic request When the session is not connected yet Then no audio mode is acquired`() = runTest {
        val session = createSession()

        session.setMicEnabled(true)

        assertEquals(0, audio.acquireCount)

        startConnectedSession(session)

        assertEquals(1, audio.acquireCount)
    }

    @Test
    fun `Given a live mic When toggling it Then the audio mode is acquired and released exactly once`() = runTest {
        val session = createSession()
        startConnectedSession(session)

        session.setMicEnabled(true)
        assertEquals(1, audio.acquireCount)
        // Enabling again while live must not stack another hold
        session.setMicEnabled(true)
        assertEquals(1, audio.acquireCount)

        session.setMicEnabled(false)
        assertEquals(0, audio.activeHolds)
        // Disabling again must not over-release
        session.setMicEnabled(false)
        assertEquals(1, audio.releaseCount)
    }

    @Test
    fun `Given a live mic When the session reconnects Then the audio mode is held through the reconnection`() = runTest {
        val session = createSession()
        startConnectedSession(session)
        session.setMicEnabled(true)

        factory.lastController.emit(PeerConnectionEvent.ConnectionStateChanged(RtcConnectionState.FAILED))
        advanceUntilIdle()

        assertEquals(2, factory.controllers.size)
        assertEquals(1, audio.acquireCount)
        assertEquals(1, audio.activeHolds)
    }

    @Test
    fun `Given a live mic When the session stops Then the audio mode is released`() = runTest {
        val session = createSession()
        startConnectedSession(session)
        session.setMicEnabled(true)

        session.stop()
        advanceUntilIdle()

        assertEquals(0, audio.activeHolds)
    }

    @Test
    fun `Given a live mic When the session is released Then the audio mode is released`() = runTest {
        val session = createSession()
        startConnectedSession(session)
        session.setMicEnabled(true)

        session.release()
        advanceUntilIdle()

        assertEquals(0, audio.activeHolds)
    }

    @Test
    fun `Given a live mic When signaling fails Then the audio mode is released`() = runTest {
        val session = createSession()
        startConnectedSession(session)
        session.setMicEnabled(true)

        signaling.currentSession.trySend(SignalingEvent.Error(code = "webrtc_offer_failed", message = null))
        advanceUntilIdle()

        assertTrue(session.state.value is PlayerState.Failed)
        assertEquals(MicState.Off, session.micState.value)
        assertEquals(0, audio.activeHolds)
    }

    @Test
    fun `Given a live mic When the connection is lost for good Then the audio mode is released`() = runTest {
        val session = createSession()
        startConnectedSession(session)
        session.setMicEnabled(true)

        repeat(4) {
            factory.lastController.emit(PeerConnectionEvent.ConnectionStateChanged(RtcConnectionState.FAILED))
            advanceUntilIdle()
        }

        assertEquals(PlayerState.Failed(PlayerFailure.ConnectionLost), session.state.value)
        assertEquals(0, audio.activeHolds)
    }

    @Test
    fun `Given attached sinks and muted audio When the session connects Then they are applied`() = runTest {
        val session = createSession()
        val sink = VideoSink { }
        session.attachVideoSink(sink)
        session.setAudioEnabled(false)

        startConnectedSession(session)

        assertTrue(sink in factory.lastController.sinks)
        assertEquals(false, factory.lastController.remoteAudioEnabled)

        session.detachVideoSink(sink)
        assertFalse(sink in factory.lastController.sinks)
    }

    @Test
    fun `Given a released session When starting again Then nothing happens`() = runTest {
        val session = createSession()
        startConnectedSession(session)

        session.release()
        advanceUntilIdle()
        assertEquals(PlayerState.Idle, session.state.value)
        assertTrue(factory.lastController.disposed)

        session.start()
        advanceUntilIdle()

        assertEquals(PlayerState.Idle, session.state.value)
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

        assertEquals(PlayerState.Playing, session.state.value)
        assertNull(session.state.value as? PlayerState.Failed)
    }
}
