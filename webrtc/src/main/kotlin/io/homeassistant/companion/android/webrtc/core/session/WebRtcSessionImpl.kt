package io.homeassistant.companion.android.webrtc.core.session

import io.homeassistant.companion.android.webrtc.core.AudioReceiver
import io.homeassistant.companion.android.webrtc.core.AudioSender
import io.homeassistant.companion.android.webrtc.core.ConnectionState
import io.homeassistant.companion.android.webrtc.core.MediaOptions
import io.homeassistant.companion.android.webrtc.core.MicState
import io.homeassistant.companion.android.webrtc.core.RtcStatsSnapshot
import io.homeassistant.companion.android.webrtc.core.SessionFailure
import io.homeassistant.companion.android.webrtc.core.VideoReceiver
import io.homeassistant.companion.android.webrtc.core.WebRtcSession
import io.homeassistant.companion.android.webrtc.core.signaling.IceCandidateInit
import io.homeassistant.companion.android.webrtc.core.signaling.SignalingClient
import io.homeassistant.companion.android.webrtc.core.signaling.SignalingEvent
import io.homeassistant.companion.android.webrtc.core.signaling.SignalingResult
import io.homeassistant.companion.android.webrtc.core.signaling.WebRtcSessionId
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import livekit.org.webrtc.EglBase
import livekit.org.webrtc.VideoSink
import timber.log.Timber

/**
 * How long the peer connection may stay in DISCONNECTED before the session reconnects. Transient
 * drops (like a Wi-Fi roam) often recover on their own within this window.
 */
private val DISCONNECTED_GRACE_PERIOD = 5.seconds

/**
 * How many times the session renegotiates after losing an established connection before giving
 * up with [SessionFailure.ConnectionLost].
 */
private const val MAX_RECONNECT_ATTEMPTS = 3

private val RECONNECT_BASE_DELAY = 2.seconds

/** How a negotiation attempt ended, so the session can decide whether to renegotiate. */
private sealed interface NegotiationOutcome {
    /** The session ended on its own (server closed the subscription or the connection dropped). */
    data object Ended : NegotiationOutcome

    /** The session failed terminally and must not be retried. */
    data class Failed(val failure: SessionFailure) : NegotiationOutcome
}

/**
 * One negotiation attempt for a single session: owns the per-attempt trickle-ICE state and drives
 * the [controller] from the signaling and peer-connection events until the session ends. A fresh
 * instance is created for every (re)negotiation so no state leaks between attempts.
 */
private class WebRtcNegotiation(
    private val signalingClient: SignalingClient,
    private val controller: PeerConnectionController,
    private val onState: (ConnectionState) -> Unit,
    private val onConnected: () -> Unit,
) {
    // All of the state below is confined to the single merged collector launched in [run], so it
    // never crosses coroutines and needs no synchronization regardless of the dispatcher.
    private var sessionId: WebRtcSessionId? = null
    private var answerApplied = false
    private var disconnectedJob: Job? = null
    private val pendingLocalCandidates = mutableListOf<IceCandidateInit>()
    private val pendingRemoteCandidates = mutableListOf<IceCandidateInit>()

    /**
     * Runs the negotiation until it ends on its own or fails, then returns the outcome so the
     * caller can decide whether to renegotiate. Cancellation is distinct: it means the session was
     * stopped and must not renegotiate.
     *
     * The peer-connection and signaling events are merged into one collector so a single coroutine
     * owns all the negotiation state.
     */
    suspend fun run(offerSdp: String): NegotiationOutcome = coroutineScope {
        val ended = CompletableDeferred<NegotiationOutcome>()
        // Completed once the peer connection is established. After that the signaling subscription is
        // no longer needed (media flows on its own transport), so it is closed.
        val connected = CompletableDeferred<Unit>()
        val signalingEvents = channelFlow {
            val collection = launch {
                signalingClient.openSession(offerSdp).collect { send(NegotiationEvent.Signaling(it)) }
                // The subscription closed on its own before the connection was established (the server
                // ended it): surface it so the session renegotiates. A terminal error emitted just
                // before this is handled first by the single collector, so it wins.
                send(NegotiationEvent.SignalingClosed)
            }
            // Once connected, stop collecting so the subscription is unsubscribed and therefore not
            // resubscribed on a WebSocket reconnect (which would re-offer onto the already-negotiated
            // peer). Cancelling skips the SignalingClosed above, so this planned close is not mistaken
            // for a server drop that would trigger a renegotiation.
            connected.await()
            collection.cancel()
        }
        val events = merge(controller.events.map(NegotiationEvent::Peer), signalingEvents)
        val job = launch {
            events.collect { event ->
                when (event) {
                    is NegotiationEvent.Peer -> handlePeerEvent(event.event, this, ended, connected)
                    is NegotiationEvent.Signaling -> handleSignalingEvent(event.event, ended)
                    NegotiationEvent.SignalingClosed -> ended.complete(NegotiationOutcome.Ended)
                }
            }
        }
        val outcome = ended.await()
        job.cancel()
        outcome
    }

    private suspend fun handlePeerEvent(
        event: PeerConnectionEvent,
        scope: CoroutineScope,
        ended: CompletableDeferred<NegotiationOutcome>,
        connected: CompletableDeferred<Unit>,
    ) {
        when (event) {
            is PeerConnectionEvent.LocalCandidate -> {
                val id = sessionId
                if (id == null) {
                    // The server only accepts candidates once it assigned a session id
                    pendingLocalCandidates += event.candidate
                } else {
                    sendLocalCandidate(id, event.candidate)
                }
            }

            is PeerConnectionEvent.ConnectionStateChanged -> when (event.state) {
                RtcConnectionState.CONNECTED -> {
                    disconnectedJob?.cancel()
                    disconnectedJob = null
                    // Negotiation is done; release the signaling subscription.
                    connected.complete(Unit)
                    onState(ConnectionState.Connected)
                    onConnected()
                }

                RtcConnectionState.DISCONNECTED -> {
                    onState(ConnectionState.Reconnecting)
                    // Restart the grace period so a stale timer from a previous drop
                    // cannot force a reconnection after the connection recovered
                    disconnectedJob?.cancel()
                    disconnectedJob = scope.launch {
                        delay(DISCONNECTED_GRACE_PERIOD)
                        ended.complete(NegotiationOutcome.Ended)
                    }
                }

                RtcConnectionState.FAILED -> ended.complete(NegotiationOutcome.Ended)

                RtcConnectionState.NEW, RtcConnectionState.CONNECTING, RtcConnectionState.CLOSED -> Unit
            }
        }
    }

    private suspend fun handleSignalingEvent(event: SignalingEvent, ended: CompletableDeferred<NegotiationOutcome>) {
        when (event) {
            is SignalingEvent.Session -> {
                sessionId = event.sessionId
                pendingLocalCandidates.forEach { sendLocalCandidate(event.sessionId, it) }
                pendingLocalCandidates.clear()
            }

            is SignalingEvent.Answer -> {
                controller.setAnswer(event.sdp)
                answerApplied = true
                // The connection state stays Connecting/Reconnecting (set per attempt in
                // runSession) until the peer connection actually reports Connected.
                pendingRemoteCandidates.forEach { addRemoteCandidate(it) }
                pendingRemoteCandidates.clear()
            }

            is SignalingEvent.Candidate -> {
                if (answerApplied) {
                    addRemoteCandidate(event.candidate)
                } else {
                    // Trickle ICE, the candidate can arrive before the answer but can
                    // only be applied once the remote description is set
                    pendingRemoteCandidates += event.candidate
                }
            }

            is SignalingEvent.Error ->
                ended.complete(NegotiationOutcome.Failed(SessionFailure.Signaling(event.code, event.message)))
        }
    }

    private suspend fun sendLocalCandidate(sessionId: WebRtcSessionId, candidate: IceCandidateInit) {
        if (!signalingClient.sendCandidate(sessionId, candidate)) {
            // Not fatal, the connection can still be established through the other candidates
            Timber.w("A local ICE candidate was not accepted")
        }
    }

    private suspend fun addRemoteCandidate(candidate: IceCandidateInit) {
        if (!controller.addRemoteCandidate(candidate)) {
            Timber.w("Peer connection rejected a remote ICE candidate")
        }
    }
}

/** The two event sources a [WebRtcNegotiation] merges, so a single collector processes both. */
private sealed interface NegotiationEvent {
    data class Peer(val event: PeerConnectionEvent) : NegotiationEvent
    data class Signaling(val event: SignalingEvent) : NegotiationEvent

    /** The signaling subscription closed normally (the server ended it or the connection dropped). */
    data object SignalingClosed : NegotiationEvent
}

/**
 * The libwebrtc-backed [WebRtcSession]: negotiates through the injected [SignalingClient], drives
 * one [PeerConnectionController] per negotiation and owns the full lifecycle (trickle ICE in both
 * directions, candidate buffering, reconnection with capped backoff, and ordered teardown on every
 * exit path). The per-direction capability handles are built from [mediaOptions] and are non-null
 * only for the directions that were negotiated.
 *
 * The Home Assistant WebRTC signaling API does not support renegotiation, so when an established
 * connection is lost the session disposes the peer connection and negotiates a fresh one (up to
 * [MAX_RECONNECT_ATTEMPTS] times) instead of using an ICE restart.
 *
 * @param signalingClient the signaling backend, scoped to the right server and media source
 * @param controllerFactory creates one peer connection per negotiation
 * @param mediaOptions the media directions to negotiate, fixed for the lifetime of the session
 * @param dispatcher the dispatcher the session's coroutines run on; defaults to [Dispatchers.Default].
 * @param eglContext the shared EGL context exposed to renderers
 */
@Suppress("TooManyFunctions")
internal class WebRtcSessionImpl(
    eglContext: EglBase.Context,
    private val signalingClient: SignalingClient,
    private val controllerFactory: PeerConnectionController.Factory,
    private val mediaOptions: MediaOptions = MediaOptions(),
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : WebRtcSession {

    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    override val state: StateFlow<ConnectionState> = _state.asStateFlow()

    private val micStateFlow = MutableStateFlow<MicState>(MicState.Off)

    override val videoIn: VideoReceiver? = if (mediaOptions.videoIn) {
        object : VideoReceiver(eglContext) {
            override suspend fun attachSink(sink: VideoSink) = attachVideoSink(sink)
            override suspend fun detachSink(sink: VideoSink) = detachVideoSink(sink)
        }
    } else {
        null
    }
    override val audioIn: AudioReceiver? = if (mediaOptions.audioIn) {
        object : AudioReceiver {
            override suspend fun setPlaybackEnabled(enabled: Boolean) =
                this@WebRtcSessionImpl.setPlaybackEnabled(enabled)
        }
    } else {
        null
    }
    override val audioOut: AudioSender? = if (mediaOptions.audioOut) {
        object : AudioSender {
            override val state: StateFlow<MicState> = micStateFlow.asStateFlow()
            override suspend fun setEnabled(enabled: Boolean) = setMicEnabled(enabled)
        }
    } else {
        null
    }

    // Attached sinks are kept here so they can be (re)applied to the controller: a sink can be
    // attached before negotiation has produced a controller, and must also be re-attached to the
    // fresh controller created on every reconnect. Guarded by [controlLock], like the replay that
    // reads it.
    private val videoSinks = mutableSetOf<VideoSink>()

    @Volatile
    private var controller: PeerConnectionController? = null

    // The microphone starts live when talk-back was negotiated, so the selected audio-out stream
    // actually flows once the session connects.
    @Volatile
    private var micEnabled = mediaOptions.audioOut

    @Volatile
    private var audioEnabled = true

    // Serializes publishing the freshly negotiated controller and replaying the cached media settings
    // (sinks, playback, mic) against the capability mutations below, so a user action completed right
    // after the controller is published is never undone by the replay, and vice versa.
    private val controlLock = Mutex()

    // Serializes start/stop/release so [sessionJob] and [released] are only read or written by one
    // of them at a time (they are touched nowhere else). It does not guard the state flows or the
    // @Volatile fields above.
    private val lifecycleLock = Mutex()
    private var sessionJob: Job? = null
    private var released = false

    override suspend fun start() = lifecycleLock.withLock {
        if (released || !mediaOptions.hasAnyStream || sessionJob?.isActive == true) return@withLock
        // A cancelled session may still be running its cleanup: wait for it so two sessions
        // never overlap and the previous controller is always disposed before a new one exists
        val previousJob = sessionJob
        sessionJob = scope.launch {
            previousJob?.join()
            runSession()
        }
    }

    override suspend fun stop() = lifecycleLock.withLock {
        val job = sessionJob ?: return@withLock
        // Await teardown under the lock (NonCancellable so a cancelled caller still waits) so the
        // controller is disposed and the state reset before a later start() can run. Cancelling
        // without joining let a cancelled stop/start chain start a new session over a not-yet-disposed
        // one, whose cleanup then cleared the new controller.
        withContext(NonCancellable) { job.cancelAndJoin() }
    }

    override suspend fun release() = lifecycleLock.withLock {
        if (released) return@withLock
        released = true
        withContext(NonCancellable) { sessionJob?.cancelAndJoin() }
        scope.cancel()
    }
    override fun observeStats(interval: Duration): Flow<RtcStatsSnapshot> = flow {
        var previous: RtcStatsSnapshot? = null
        while (true) {
            val snapshot = controller?.getStats() ?: RtcStatsSnapshot.EMPTY
            emit(snapshot.withRatesFrom(previous))
            previous = snapshot
            delay(interval)
        }
    }

    private suspend fun attachVideoSink(sink: VideoSink) = controlLock.withLock {
        if (videoSinks.add(sink)) {
            controller?.addVideoSink(sink)
        }
    }

    private suspend fun detachVideoSink(sink: VideoSink) = controlLock.withLock {
        if (videoSinks.remove(sink)) {
            controller?.removeVideoSink(sink)
        }
    }

    private suspend fun setPlaybackEnabled(enabled: Boolean) = controlLock.withLock {
        audioEnabled = enabled
        controller?.setRemoteAudioEnabled(enabled)
        Unit
    }

    private suspend fun setMicEnabled(enabled: Boolean) = controlLock.withLock {
        micEnabled = enabled
        val activeController = controller
        if (!enabled) {
            activeController?.setMicrophoneEnabled(false)
            micStateFlow.value = MicState.Off
        } else if (activeController != null) {
            activeController.setMicrophoneEnabled(true)
            micStateFlow.value = MicState.Live
        } else {
            // Remembered and applied when the session (re)connects
            micStateFlow.value = MicState.Unavailable
        }
    }

    private suspend fun runSession() {
        try {
            runReconnectLoop()
        } catch (e: CancellationException) {
            // stop()/release() cancelled us. Emit the terminal state here, once the collector has
            // fully torn down, so it is the last write and no in-flight event can overwrite it.
            _state.value = ConnectionState.Idle
            micStateFlow.value = MicState.Off
            throw e
        }
    }

    private suspend fun runReconnectLoop() {
        // A successful connection resets this via negotiateAndStream's onConnected callback, so the
        // cap counts consecutive failures: each fresh connection earns a new reconnection budget.
        var attempt = 0
        while (attempt <= MAX_RECONNECT_ATTEMPTS) {
            _state.value = if (attempt == 0) ConnectionState.Connecting else ConnectionState.Reconnecting
            val outcome = try {
                // Returns when the session ends on its own (server closed it or the connection was
                // lost); fall through to the reconnection handling below.
                negotiateAndStream(onConnected = { attempt = 0 })
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "WebRTC session failed unexpectedly")
                NegotiationOutcome.Failed(SessionFailure.Internal(e.message))
            } finally {
                // The job may be cancelling (stop/release), so dispose under NonCancellable to
                // guarantee the native peer connection is released.
                withContext(NonCancellable) { controller?.dispose() }
                controller = null
                if (micStateFlow.value == MicState.Live) {
                    micStateFlow.value = MicState.Off
                }
            }

            if (outcome is NegotiationOutcome.Failed) {
                val failure = outcome.failure
                if (failure is SessionFailure.Signaling) Timber.w("WebRTC signaling failed (${failure.code})")
                failWith(failure)
                return
            }

            attempt++
            if (attempt <= MAX_RECONNECT_ATTEMPTS) {
                // Publish Reconnecting before the backoff: a FAILED peer or a closed subscription does
                // not change the state on its own, so without this observers would keep seeing
                // Connected during the delay while the peer connection is already disposed.
                _state.value = ConnectionState.Reconnecting
                Timber.d("WebRTC connection lost, reconnecting (attempt $attempt)")
                delay(RECONNECT_BASE_DELAY * (1 shl (attempt - 1)))
            }
        }
        Timber.w("WebRTC connection lost and could not be recovered")
        failWith(SessionFailure.ConnectionLost)
    }

    private suspend fun negotiateAndStream(onConnected: () -> Unit): NegotiationOutcome {
        val config = when (val result = signalingClient.getClientConfig()) {
            is SignalingResult.Success -> result.value
            is SignalingResult.Failure ->
                return NegotiationOutcome.Failed(SessionFailure.Signaling(result.code, result.message))
        }
        // Create, publish and replay the cached media settings in one locked step. Creating inside
        // the lock and assigning this.controller with no suspension in between means a cancellation
        // (stop/release) can never strand a created-but-unpublished controller that the teardown
        // finally would fail to dispose. It is also serialized against concurrent capability
        // mutations, so a toggle the user made meanwhile is not undone (and does not undo this
        // replay). The microphone is enabled before the offer is created so its capture is already
        // running when the connection is established, instead of starting a beat late.
        val controller = controlLock.withLock {
            controllerFactory.create(config, mediaOptions).also { created ->
                this.controller = created
                videoSinks.forEach { created.addVideoSink(it) }
                created.setRemoteAudioEnabled(audioEnabled)
                if (micEnabled) {
                    created.setMicrophoneEnabled(true)
                    micStateFlow.value = MicState.Live
                }
            }
        }
        val offerSdp = controller.createOffer()
        return WebRtcNegotiation(
            signalingClient = signalingClient,
            controller = controller,
            onState = { _state.value = it },
            onConnected = onConnected,
        ).run(offerSdp)
    }

    private fun failWith(failure: SessionFailure) {
        _state.value = ConnectionState.Failed(failure)
        micStateFlow.value = MicState.Off
    }
}
