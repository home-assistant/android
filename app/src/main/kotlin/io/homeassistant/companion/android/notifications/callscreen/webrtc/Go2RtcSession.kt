package io.homeassistant.companion.android.notifications.callscreen.webrtc

import io.homeassistant.companion.android.notifications.callscreen.CallMedia
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.MediaStreamTrack
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.RtpTransceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.VideoTrack
import timber.log.Timber

private const val STUN_SERVER = "stun:stun.l.google.com:19302"
private const val WS_NORMAL_CLOSURE = 1000
private const val MIC_TRACK_ID = "mic"
private const val ICE_CANDIDATE_MID = "0"
private const val ICE_CANDIDATE_MLINE_INDEX = 0

/** Lifecycle of a [Go2RtcSession]. */
internal sealed interface Go2RtcSessionState {
    data object Connecting : Go2RtcSessionState
    data object Connected : Go2RtcSessionState
    data class Failed(val reason: String) : Go2RtcSessionState
    data object Closed : Go2RtcSessionState
}

/**
 * WebRTC session with a go2rtc stream through the WebRTC integration WebSocket, following
 * `video-rtc.js` of the integration: the offer is sent over the WebSocket, the answer and the
 * ICE candidates are exchanged as `webrtc/answer` and `webrtc/candidate` frames. Failures are final, there is no
 * reconnection.
 *
 * Every method must be called from the thread of [scope] (the main thread for the call screen).
 * [close] must be called once the session is not needed anymore.
 *
 * @param scope scope owned by the caller, used to process asynchronous callbacks on its thread
 */
internal class Go2RtcSession(
    private val factory: PeerConnectionFactory,
    private val okHttpClient: OkHttpClient,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow<Go2RtcSessionState>(Go2RtcSessionState.Connecting)

    /** Current state of the session. */
    val state: StateFlow<Go2RtcSessionState> = _state.asStateFlow()

    private val _videoTrack = MutableStateFlow<VideoTrack?>(null)

    /** Remote video track, available once the peer connection negotiated one. */
    val videoTrack: StateFlow<VideoTrack?> = _videoTrack.asStateFlow()

    private val closed = AtomicBoolean(false)

    @Volatile
    private var peerConnection: PeerConnection? = null

    @Volatile
    private var webSocket: WebSocket? = null

    @Volatile
    private var audioSource: AudioSource? = null

    @Volatile
    private var audioTrack: AudioTrack? = null

    private var remoteDescriptionSet = false
    private val pendingRemoteCandidates = mutableListOf<IceCandidate>()

    /** Mute or unmute the microphone sent to the stream, no-op when the session has no microphone. */
    fun setMicrophoneEnabled(enabled: Boolean) {
        audioTrack?.setEnabled(enabled)
    }

    /**
     * Negotiate the session with go2rtc, returns once the offer is sent. Connection progress is
     * reported by [state].
     *
     * @param wsUrl WebSocket URL built by [buildGo2RtcWsUrl]
     * @param media media to negotiate, [CallMedia.MICROPHONE] must only be present if the permission is granted
     */
    suspend fun connect(wsUrl: String, media: Set<CallMedia>) {
        try {
            val pc = createPeerConnection() ?: return fail("Unable to create the peer connection")
            peerConnection = pc
            addTransceivers(pc, media)
            val offer = pc.createOfferSuspending()
            if (closed.get()) return
            openWebSocket(wsUrl, offer.description)
            pc.setLocalDescriptionSuspending(offer)
        } catch (e: CancellationException) {
            throw e
        } catch (e: IllegalStateException) {
            Timber.e(e, "Failed to negotiate go2rtc session")
            fail(e.message ?: "Negotiation failed")
        }
    }

    /** Release every native resource, safe to call several times. */
    fun close() {
        if (!closed.compareAndSet(false, true)) return
        webSocket?.close(WS_NORMAL_CLOSURE, null)
        webSocket = null
        _videoTrack.value = null
        peerConnection?.close()
        peerConnection?.dispose()
        peerConnection = null
        audioTrack?.dispose()
        audioTrack = null
        audioSource?.dispose()
        audioSource = null
        _state.value = Go2RtcSessionState.Closed
    }

    private fun createPeerConnection(): PeerConnection? {
        val config = PeerConnection.RTCConfiguration(
            listOf(PeerConnection.IceServer.builder(STUN_SERVER).createIceServer()),
        ).apply { sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN }
        return factory.createPeerConnection(config, PeerObserver())
    }

    private fun addTransceivers(pc: PeerConnection, media: Set<CallMedia>) {
        if (CallMedia.MICROPHONE in media) {
            val source = factory.createAudioSource(MediaConstraints())
            val track = factory.createAudioTrack(MIC_TRACK_ID, source)
            audioSource = source
            audioTrack = track
            pc.addTransceiver(
                track,
                RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.SEND_ONLY),
            )
        }
        val receiveOnly = RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.RECV_ONLY)
        if (CallMedia.VIDEO in media) {
            pc.addTransceiver(MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO, receiveOnly)
        }
        if (CallMedia.AUDIO in media) {
            pc.addTransceiver(MediaStreamTrack.MediaType.MEDIA_TYPE_AUDIO, receiveOnly)
        }
    }

    private fun openWebSocket(wsUrl: String, offerSdp: String) {
        val socket = okHttpClient.newWebSocket(Request.Builder().url(wsUrl).build(), SignalingListener())
        webSocket = socket
        socket.send(encodeOffer(offerSdp))
    }

    private fun fail(reason: String) {
        if (!closed.get()) {
            _state.value = Go2RtcSessionState.Failed(reason)
        }
    }

    private fun handleMessage(message: Go2RtcMessage) {
        val pc = peerConnection ?: return
        when (message) {
            is Go2RtcMessage.Answer -> scope.launch {
                try {
                    pc.setRemoteDescriptionSuspending(SessionDescription(SessionDescription.Type.ANSWER, message.sdp))
                    remoteDescriptionSet = true
                    pendingRemoteCandidates.forEach { pc.addIceCandidate(it) }
                    pendingRemoteCandidates.clear()
                } catch (e: IllegalStateException) {
                    Timber.e(e, "Failed to apply go2rtc answer")
                    fail(e.message ?: "Invalid answer")
                }
            }

            is Go2RtcMessage.Candidate -> if (message.candidate.isNotBlank()) {
                val candidate = IceCandidate(ICE_CANDIDATE_MID, ICE_CANDIDATE_MLINE_INDEX, message.candidate)
                if (remoteDescriptionSet) pc.addIceCandidate(candidate) else pendingRemoteCandidates.add(candidate)
            }

            is Go2RtcMessage.Error -> {
                Timber.w("go2rtc reported an error: ${message.value}")
                fail(message.value)
            }
        }
    }

    private inner class SignalingListener : WebSocketListener() {
        override fun onMessage(webSocket: WebSocket, text: String) {
            val message = parseGo2RtcMessage(text) ?: return
            scope.launch { handleMessage(message) }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Timber.e(t, "go2rtc WebSocket failed")
            scope.launch { fail(t.message ?: "WebSocket failure") }
        }
    }

    @Suppress("TooManyFunctions") // PeerConnection.Observer has many callbacks, most unused
    private inner class PeerObserver : PeerConnection.Observer {
        override fun onIceCandidate(candidate: IceCandidate?) {
            candidate?.let { webSocket?.send(encodeCandidate(it.sdp)) }
        }

        override fun onIceGatheringChange(newState: PeerConnection.IceGatheringState?) {
            if (newState == PeerConnection.IceGatheringState.COMPLETE) {
                webSocket?.send(encodeCandidate(""))
            }
        }

        override fun onTrack(transceiver: RtpTransceiver?) {
            val track = transceiver?.receiver?.track() as? VideoTrack ?: return
            scope.launch { if (!closed.get()) _videoTrack.value = track }
        }

        override fun onConnectionChange(newState: PeerConnection.PeerConnectionState?) {
            scope.launch {
                when (newState) {
                    PeerConnection.PeerConnectionState.CONNECTED -> if (!closed.get()) {
                        _state.value = Go2RtcSessionState.Connected
                    }

                    PeerConnection.PeerConnectionState.FAILED,
                    PeerConnection.PeerConnectionState.DISCONNECTED,
                    -> fail("Peer connection $newState")

                    else -> Unit
                }
            }
        }

        override fun onSignalingChange(newState: PeerConnection.SignalingState?) = Unit
        override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState?) = Unit
        override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit
        override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) = Unit
        override fun onAddStream(stream: MediaStream?) = Unit
        override fun onRemoveStream(stream: MediaStream?) = Unit
        override fun onDataChannel(dataChannel: DataChannel?) = Unit
        override fun onRenegotiationNeeded() = Unit
        override fun onAddTrack(receiver: RtpReceiver?, mediaStreams: Array<out MediaStream>?) = Unit
    }
}

private open class SdpResultObserver : SdpObserver {
    override fun onCreateSuccess(sdp: SessionDescription?) = Unit
    override fun onCreateFailure(error: String?) = Unit
    override fun onSetSuccess() = Unit
    override fun onSetFailure(error: String?) = Unit
}

private suspend fun PeerConnection.createOfferSuspending(): SessionDescription =
    suspendCancellableCoroutine { continuation ->
        createOffer(
            object : SdpResultObserver() {
                override fun onCreateSuccess(sdp: SessionDescription?) {
                    if (sdp != null) {
                        continuation.resume(sdp)
                    } else {
                        continuation.resumeWithException(IllegalStateException("Empty offer"))
                    }
                }

                override fun onCreateFailure(error: String?) {
                    continuation.resumeWithException(IllegalStateException("Create offer failed: $error"))
                }
            },
            MediaConstraints(),
        )
    }

private suspend fun PeerConnection.setLocalDescriptionSuspending(sdp: SessionDescription) =
    suspendCancellableCoroutine { continuation ->
        setLocalDescription(
            object : SdpResultObserver() {
                override fun onSetSuccess() = continuation.resume(Unit)
                override fun onSetFailure(error: String?) =
                    continuation.resumeWithException(IllegalStateException("Set local description failed: $error"))
            },
            sdp,
        )
    }

private suspend fun PeerConnection.setRemoteDescriptionSuspending(sdp: SessionDescription) =
    suspendCancellableCoroutine { continuation ->
        setRemoteDescription(
            object : SdpResultObserver() {
                override fun onSetSuccess() = continuation.resume(Unit)
                override fun onSetFailure(error: String?) =
                    continuation.resumeWithException(IllegalStateException("Set remote description failed: $error"))
            },
            sdp,
        )
    }
