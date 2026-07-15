package io.homeassistant.companion.android.webrtc.core.session.libwebrtc

import io.homeassistant.companion.android.webrtc.core.session.PeerConnectionController
import io.homeassistant.companion.android.webrtc.core.session.PeerConnectionEvent
import io.homeassistant.companion.android.webrtc.core.session.RtcConnectionState
import io.homeassistant.companion.android.webrtc.core.signaling.IceCandidateInit
import io.homeassistant.companion.android.webrtc.core.signaling.RtcClientConfig
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import livekit.org.webrtc.AudioTrack
import livekit.org.webrtc.DataChannel
import livekit.org.webrtc.IceCandidate
import livekit.org.webrtc.MediaConstraints
import livekit.org.webrtc.MediaStream
import livekit.org.webrtc.MediaStreamTrack
import livekit.org.webrtc.PeerConnection
import livekit.org.webrtc.PeerConnectionFactory
import livekit.org.webrtc.RtpReceiver
import livekit.org.webrtc.RtpTransceiver
import livekit.org.webrtc.SdpObserver
import livekit.org.webrtc.SessionDescription
import livekit.org.webrtc.VideoSink
import livekit.org.webrtc.VideoTrack
import timber.log.Timber

private const val MICROPHONE_TRACK_ID = "ha_microphone"

/**
 * [PeerConnectionController] implementation backed by libwebrtc.
 *
 * The peer connection is created with a receive-only video transceiver and a send-and-receive
 * audio transceiver whose microphone track starts disabled, so talk-back can be toggled later
 * without renegotiating.
 */
internal class LibWebRtcPeerConnectionController(factory: PeerConnectionFactory, config: RtcClientConfig) :
    PeerConnectionController {

    private val eventsChannel = Channel<PeerConnectionEvent>(Channel.UNLIMITED)
    override val events: Flow<PeerConnectionEvent> = eventsChannel.receiveAsFlow()

    private val videoSinks = CopyOnWriteArraySet<VideoSink>()
    private val disposed = AtomicBoolean(false)

    @Volatile
    private var remoteVideoTrack: VideoTrack? = null

    @Volatile
    private var remoteAudioTrack: AudioTrack? = null

    @Volatile
    private var remoteAudioEnabled = true

    private val observer = object : PeerConnection.Observer {
        override fun onIceCandidate(candidate: IceCandidate?) {
            candidate ?: return
            eventsChannel.trySend(
                PeerConnectionEvent.LocalCandidate(
                    IceCandidateInit(
                        candidate = candidate.sdp,
                        sdpMid = candidate.sdpMid,
                        sdpMLineIndex = candidate.sdpMLineIndex,
                    ),
                ),
            )
        }

        override fun onConnectionChange(newState: PeerConnection.PeerConnectionState?) {
            newState ?: return
            eventsChannel.trySend(PeerConnectionEvent.ConnectionStateChanged(newState.toRtcConnectionState()))
        }

        override fun onTrack(transceiver: RtpTransceiver?) {
            when (val track = transceiver?.receiver?.track()) {
                is VideoTrack -> {
                    remoteVideoTrack = track
                    videoSinks.forEach(track::addSink)
                }

                is AudioTrack -> {
                    remoteAudioTrack = track
                    track.setEnabled(remoteAudioEnabled)
                }

                else -> Unit
            }
        }

        override fun onSignalingChange(newState: PeerConnection.SignalingState?) {}
        override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState?) {}
        override fun onIceConnectionReceivingChange(receiving: Boolean) {}
        override fun onIceGatheringChange(newState: PeerConnection.IceGatheringState?) {}
        override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
        override fun onAddStream(stream: MediaStream?) {}
        override fun onRemoveStream(stream: MediaStream?) {}
        override fun onDataChannel(dataChannel: DataChannel?) {}
        override fun onRenegotiationNeeded() {}
        override fun onAddTrack(receiver: RtpReceiver?, mediaStreams: Array<out MediaStream>?) {}
    }

    private val peerConnection = checkNotNull(factory.createPeerConnection(config.toRtcConfiguration(), observer)) {
        "PeerConnection could not be created"
    }
    private val audioSource = factory.createAudioSource(MediaConstraints())
    private val microphoneTrack = factory.createAudioTrack(MICROPHONE_TRACK_ID, audioSource).apply {
        setEnabled(false)
    }
    private var dataChannel: DataChannel? = null

    init {
        // The data channel (when the provider uses one, like go2rtc) and the transceivers must
        // exist before the offer is created so they are part of the negotiated SDP
        config.dataChannelLabel?.let { label ->
            dataChannel = peerConnection.createDataChannel(label, DataChannel.Init())
        }
        peerConnection.addTransceiver(
            MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO,
            RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.RECV_ONLY),
        )
        peerConnection.addTransceiver(
            microphoneTrack,
            RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.SEND_RECV),
        )
    }

    override suspend fun createOffer(): String {
        val offer = suspendCancellableCoroutine<SessionDescription> { continuation ->
            peerConnection.createOffer(
                object : SdpObserverAdapter() {
                    override fun onCreateSuccess(description: SessionDescription?) {
                        if (description == null) {
                            continuation.resumeWithException(IllegalStateException("createOffer returned no SDP"))
                        } else {
                            continuation.resume(description)
                        }
                    }

                    override fun onCreateFailure(error: String?) {
                        continuation.resumeWithException(IllegalStateException("createOffer failed: $error"))
                    }
                },
                MediaConstraints(),
            )
        }
        suspendCancellableCoroutine<Unit> { continuation ->
            peerConnection.setLocalDescription(
                object : SdpObserverAdapter() {
                    override fun onSetSuccess() {
                        continuation.resume(Unit)
                    }

                    override fun onSetFailure(error: String?) {
                        continuation.resumeWithException(IllegalStateException("setLocalDescription failed: $error"))
                    }
                },
                offer,
            )
        }
        return offer.description
    }

    override suspend fun setAnswer(sdp: String) {
        suspendCancellableCoroutine<Unit> { continuation ->
            peerConnection.setRemoteDescription(
                object : SdpObserverAdapter() {
                    override fun onSetSuccess() {
                        continuation.resume(Unit)
                    }

                    override fun onSetFailure(error: String?) {
                        continuation.resumeWithException(IllegalStateException("setRemoteDescription failed: $error"))
                    }
                },
                SessionDescription(SessionDescription.Type.ANSWER, sdp),
            )
        }
    }

    override fun addRemoteCandidate(candidate: IceCandidateInit): Boolean = peerConnection.addIceCandidate(
        IceCandidate(
            candidate.sdpMid ?: "",
            candidate.sdpMLineIndex ?: 0,
            candidate.candidate,
        ),
    )

    override fun setMicrophoneEnabled(enabled: Boolean) {
        microphoneTrack.setEnabled(enabled)
    }

    override fun setRemoteAudioEnabled(enabled: Boolean) {
        remoteAudioEnabled = enabled
        remoteAudioTrack?.setEnabled(enabled)
    }

    override fun addVideoSink(sink: VideoSink) {
        if (videoSinks.add(sink)) {
            remoteVideoTrack?.addSink(sink)
        }
    }

    override fun removeVideoSink(sink: VideoSink) {
        if (videoSinks.remove(sink)) {
            remoteVideoTrack?.removeSink(sink)
        }
    }

    override fun dispose() {
        if (disposed.getAndSet(true)) return
        remoteVideoTrack?.let { track ->
            videoSinks.forEach { sink ->
                runCatching { track.removeSink(sink) }
                    .onFailure { Timber.w(it, "Failed to detach a video sink during disposal") }
            }
        }
        // The remote tracks are owned by the peer connection and disposed with it
        remoteVideoTrack = null
        remoteAudioTrack = null
        dataChannel?.dispose()
        dataChannel = null
        // Disposal order matters: peer connection (closes transports and its receivers/senders),
        // then our local track wrapper, then its source
        peerConnection.dispose()
        microphoneTrack.dispose()
        audioSource.dispose()
        eventsChannel.close()
    }
}

private fun RtcClientConfig.toRtcConfiguration(): PeerConnection.RTCConfiguration {
    val servers = iceServers.filter { it.urls.isNotEmpty() }.map { server ->
        PeerConnection.IceServer.builder(server.urls)
            .apply {
                server.username?.let { setUsername(it) }
                server.credential?.let { setPassword(it) }
            }
            .createIceServer()
    }
    return PeerConnection.RTCConfiguration(servers).apply {
        sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
    }
}

private fun PeerConnection.PeerConnectionState.toRtcConnectionState(): RtcConnectionState = when (this) {
    PeerConnection.PeerConnectionState.NEW -> RtcConnectionState.NEW
    PeerConnection.PeerConnectionState.CONNECTING -> RtcConnectionState.CONNECTING
    PeerConnection.PeerConnectionState.CONNECTED -> RtcConnectionState.CONNECTED
    PeerConnection.PeerConnectionState.DISCONNECTED -> RtcConnectionState.DISCONNECTED
    PeerConnection.PeerConnectionState.FAILED -> RtcConnectionState.FAILED
    PeerConnection.PeerConnectionState.CLOSED -> RtcConnectionState.CLOSED
}

/**
 * [SdpObserver] with no-op defaults so callers only override the callbacks of the operation they
 * perform (create or set).
 */
private open class SdpObserverAdapter : SdpObserver {
    override fun onCreateSuccess(description: SessionDescription?) {}
    override fun onSetSuccess() {}
    override fun onCreateFailure(error: String?) {}
    override fun onSetFailure(error: String?) {}
}
