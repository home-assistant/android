package io.homeassistant.companion.android.webrtc.core.session

import io.homeassistant.companion.android.webrtc.core.CameraPlayer
import io.homeassistant.companion.android.webrtc.core.MicState
import io.homeassistant.companion.android.webrtc.core.PlayerFailure
import io.homeassistant.companion.android.webrtc.core.PlayerState
import io.homeassistant.companion.android.webrtc.core.TwoWayAudio
import io.homeassistant.companion.android.webrtc.core.signaling.IceCandidateInit
import io.homeassistant.companion.android.webrtc.core.signaling.SignalingClient
import io.homeassistant.companion.android.webrtc.core.signaling.SignalingEvent
import io.homeassistant.companion.android.webrtc.core.signaling.SignalingException
import java.util.concurrent.CopyOnWriteArraySet
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import livekit.org.webrtc.VideoSink
import timber.log.Timber

/**
 * How long the peer connection may stay in DISCONNECTED before the session reconnects. Transient
 * drops (like a Wi-Fi roam) often recover on their own within this window.
 */
private val DISCONNECTED_GRACE_PERIOD = 5.seconds

/**
 * How many times the session renegotiates after losing an established connection before giving
 * up with [PlayerFailure.ConnectionLost].
 */
private const val MAX_RECONNECT_ATTEMPTS = 3

private val RECONNECT_BASE_DELAY = 2.seconds

/**
 * One WebRTC session with one camera entity: negotiates through the injected [SignalingClient],
 * drives one [PeerConnectionController] per negotiation and owns the full lifecycle
 * (trickle ICE in both directions, candidate buffering, reconnection with capped backoff, and
 * ordered teardown on every exit path).
 *
 * The Home Assistant WebRTC signaling API does not support renegotiation, so when an established
 * connection is lost the session disposes the peer connection and negotiates a fresh one (up to
 * [MAX_RECONNECT_ATTEMPTS] times) instead of using an ICE restart.
 *
 * @param entityId the camera entity to stream
 * @param signalingClient the signaling backend, scoped to the right server
 * @param controllerFactory creates one peer connection per negotiation
 * @param dispatcher dispatcher running the session state machine, it must be serial (the default
 * already is)
 */
class WebRtcSession(
    private val entityId: String,
    private val signalingClient: SignalingClient,
    private val controllerFactory: PeerConnectionController.Factory,
    dispatcher: CoroutineDispatcher = Dispatchers.Default.limitedParallelism(1),
) : CameraPlayer,
    TwoWayAudio {

    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    private val _state = MutableStateFlow<PlayerState>(PlayerState.Idle)
    override val state: StateFlow<PlayerState> = _state.asStateFlow()

    private val _micState = MutableStateFlow<MicState>(MicState.Off)
    override val micState: StateFlow<MicState> = _micState.asStateFlow()

    private val videoSinks = CopyOnWriteArraySet<VideoSink>()

    @Volatile
    private var controller: PeerConnectionController? = null

    @Volatile
    private var micEnabled = false

    @Volatile
    private var audioEnabled = true

    private var sessionJob: Job? = null
    private var released = false

    override fun start() {
        synchronized(this) {
            if (released || sessionJob?.isActive == true) return
            sessionJob = scope.launch { runSession() }
        }
    }

    override fun stop() {
        synchronized(this) {
            sessionJob?.cancel()
            sessionJob = null
            micEnabled = false
            _micState.value = MicState.Off
            _state.value = PlayerState.Idle
        }
    }

    override fun release() {
        synchronized(this) {
            if (released) return
            released = true
            stop()
            scope.cancel()
        }
    }

    override fun attachVideoSink(sink: VideoSink) {
        if (videoSinks.add(sink)) {
            controller?.addVideoSink(sink)
        }
    }

    override fun detachVideoSink(sink: VideoSink) {
        if (videoSinks.remove(sink)) {
            controller?.removeVideoSink(sink)
        }
    }

    override fun setAudioEnabled(enabled: Boolean) {
        audioEnabled = enabled
        controller?.setRemoteAudioEnabled(enabled)
    }

    override fun setMicEnabled(enabled: Boolean) {
        micEnabled = enabled
        val activeController = controller
        if (!enabled) {
            activeController?.setMicrophoneEnabled(false)
            _micState.value = MicState.Off
        } else if (activeController != null) {
            activeController.setMicrophoneEnabled(true)
            _micState.value = MicState.Live
        } else {
            // Remembered and applied when the session (re)connects
            _micState.value = MicState.Unavailable
        }
    }

    private suspend fun runSession() {
        var attempt = 0
        while (true) {
            _state.value = if (attempt == 0) PlayerState.Connecting else PlayerState.Buffering
            try {
                negotiateAndStream(onConnected = { attempt = 0 })
                // The signaling subscription ended, the server closed the session
                throw SessionRetryException()
            } catch (e: CancellationException) {
                throw e
            } catch (e: SignalingException) {
                Timber.w(e, "WebRTC signaling failed (${e.code})")
                failWith(PlayerFailure.Signaling(e.code, e.message))
                return
            } catch (e: SessionRetryException) {
                // Fall through to the reconnection handling below
            } catch (e: Exception) {
                Timber.e(e, "WebRTC session failed unexpectedly")
                failWith(PlayerFailure.Internal(e.message))
                return
            } finally {
                disposeController()
            }

            attempt++
            if (attempt > MAX_RECONNECT_ATTEMPTS) {
                Timber.w("WebRTC connection lost and could not be recovered")
                failWith(PlayerFailure.ConnectionLost)
                return
            }
            Timber.d("WebRTC connection lost, reconnecting (attempt $attempt)")
            delay(RECONNECT_BASE_DELAY * (1 shl (attempt - 1)))
        }
    }

    private suspend fun negotiateAndStream(onConnected: () -> Unit) {
        val config = signalingClient.getClientConfig(entityId)
        val controller = controllerFactory.create(config)
        this.controller = controller
        videoSinks.forEach(controller::addVideoSink)
        controller.setRemoteAudioEnabled(audioEnabled)
        val offerSdp = controller.createOffer()
        if (micEnabled) {
            controller.setMicrophoneEnabled(true)
            _micState.value = MicState.Live
        }

        var sessionId: String? = null
        var answerApplied = false
        val pendingLocalCandidates = mutableListOf<IceCandidateInit>()
        val pendingRemoteCandidates = mutableListOf<IceCandidateInit>()

        coroutineScope {
            var disconnectedJob: Job? = null

            launch {
                controller.events.collect { event ->
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
                                _state.value = PlayerState.Playing
                                onConnected()
                            }

                            RtcConnectionState.DISCONNECTED -> {
                                _state.value = PlayerState.Buffering
                                disconnectedJob = launch {
                                    delay(DISCONNECTED_GRACE_PERIOD)
                                    throw SessionRetryException()
                                }
                            }

                            RtcConnectionState.FAILED -> throw SessionRetryException()

                            else -> Unit
                        }
                    }
                }
            }

            signalingClient.openSession(entityId, offerSdp).collect { event ->
                when (event) {
                    is SignalingEvent.Session -> {
                        sessionId = event.sessionId
                        pendingLocalCandidates.forEach { sendLocalCandidate(event.sessionId, it) }
                        pendingLocalCandidates.clear()
                    }

                    is SignalingEvent.Answer -> {
                        controller.setAnswer(event.sdp)
                        answerApplied = true
                        _state.value = PlayerState.Buffering
                        pendingRemoteCandidates.forEach { addRemoteCandidate(controller, it) }
                        pendingRemoteCandidates.clear()
                    }

                    is SignalingEvent.Candidate -> {
                        if (answerApplied) {
                            addRemoteCandidate(controller, event.candidate)
                        } else {
                            // Trickle ICE, the candidate can arrive before the answer but can
                            // only be applied once the remote description is set
                            pendingRemoteCandidates += event.candidate
                        }
                    }

                    is SignalingEvent.Error -> throw SignalingException(event.code, event.message)
                }
            }

            // The subscription Flow completed, the server ended the session; stop the controller
            // collector and let runSession decide whether to renegotiate
            throw SessionRetryException()
        }
    }

    private suspend fun sendLocalCandidate(sessionId: String, candidate: IceCandidateInit) {
        try {
            if (!signalingClient.sendCandidate(entityId, sessionId, candidate)) {
                Timber.w("Server rejected a local ICE candidate")
            }
        } catch (e: SignalingException) {
            // Not fatal, the connection can still be established through the other candidates
            Timber.w(e, "Failed to send a local ICE candidate")
        }
    }

    private fun addRemoteCandidate(controller: PeerConnectionController, candidate: IceCandidateInit) {
        if (!controller.addRemoteCandidate(candidate)) {
            Timber.w("Peer connection rejected a remote ICE candidate")
        }
    }

    private fun failWith(failure: PlayerFailure) {
        _state.value = PlayerState.Failed(failure)
        _micState.value = MicState.Off
    }

    private fun disposeController() {
        controller?.dispose()
        controller = null
        if (_micState.value == MicState.Live) {
            _micState.value = MicState.Off
        }
    }
}

/**
 * Internal control-flow signal: the current negotiation is dead (connection lost or the server
 * closed the session) and the session should renegotiate from scratch.
 */
private class SessionRetryException : Exception("WebRTC session lost")
