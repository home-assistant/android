package io.homeassistant.companion.android.webrtc.core.session.libwebrtc

import io.homeassistant.companion.android.webrtc.core.MediaOptions
import io.homeassistant.companion.android.webrtc.core.session.PeerConnectionController
import io.homeassistant.companion.android.webrtc.core.session.PeerConnectionEvent
import io.homeassistant.companion.android.webrtc.core.session.RtcConnectionState
import io.homeassistant.companion.android.webrtc.core.session.RtcDebugStats
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
import livekit.org.webrtc.AudioSource
import livekit.org.webrtc.AudioTrack
import livekit.org.webrtc.DataChannel
import livekit.org.webrtc.IceCandidate
import livekit.org.webrtc.MediaConstraints
import livekit.org.webrtc.MediaStream
import livekit.org.webrtc.MediaStreamTrack
import livekit.org.webrtc.PeerConnection
import livekit.org.webrtc.PeerConnectionFactory
import livekit.org.webrtc.RTCStats
import livekit.org.webrtc.RTCStatsReport
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
 * The transceivers mirror [MediaOptions]: an optional receive-only video transceiver and an audio
 * transceiver with the configured direction. A sending-capable audio transceiver is negotiated up
 * front but has no track: the microphone capture is only created and attached (via
 * `RtpSender.setTrack`, which does not renegotiate) the first time the microphone is enabled.
 * Creating the capture eagerly would start the platform audio record pipeline for every session —
 * libwebrtc supports only one capture at a time, so a session that never uses the microphone
 * would break talk-back for every session after it, and the microphone privacy indicator would
 * show without the microphone being used.
 */
internal class LibWebRtcPeerConnectionController(
    private val factory: PeerConnectionFactory,
    config: RtcClientConfig,
    mediaOptions: MediaOptions,
) : PeerConnectionController {

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

    @Volatile
    private var audioSource: AudioSource? = null

    @Volatile
    private var microphoneTrack: AudioTrack? = null

    private var dataChannel: DataChannel? = null
    private val audioTransceiver: RtpTransceiver

    init {
        // The data channel (when the provider uses one, like go2rtc) and the transceivers must
        // exist before the offer is created so they are part of the negotiated SDP
        config.dataChannelLabel?.let { label ->
            dataChannel = peerConnection.createDataChannel(label, DataChannel.Init())
        }
        if (mediaOptions.video) {
            peerConnection.addTransceiver(
                MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO,
                RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.RECV_ONLY),
            )
        }
        audioTransceiver = peerConnection.addTransceiver(
            MediaStreamTrack.MediaType.MEDIA_TYPE_AUDIO,
            RtpTransceiver.RtpTransceiverInit(mediaOptions.audio.toTransceiverDirection()),
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

    @Volatile
    override var isMicrophoneSupported = mediaOptions.audio != MediaOptions.AudioDirection.RECEIVE_ONLY
        private set

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
        // A receive-only negotiation can never send, whatever the answer says
        isMicrophoneSupported = isMicrophoneSupported && sdp.answerAcceptsClientAudio()
    }

    override suspend fun getStats(): RtcDebugStats? {
        if (disposed.get()) return null
        return runCatching {
            suspendCancellableCoroutine<RtcDebugStats> { continuation ->
                peerConnection.getStats { report ->
                    continuation.resume(report.toDebugStats())
                }
            }
        }.getOrNull()
    }

    override fun addRemoteCandidate(candidate: IceCandidateInit): Boolean = peerConnection.addIceCandidate(
        IceCandidate(
            candidate.sdpMid ?: "",
            candidate.sdpMLineIndex ?: 0,
            candidate.candidate,
        ),
    )

    override fun setMicrophoneEnabled(enabled: Boolean) {
        if (!enabled) {
            microphoneTrack?.setEnabled(false)
            return
        }
        if (disposed.get()) return
        val track = microphoneTrack ?: run {
            // First use: create the capture now and attach it to the negotiated transceiver.
            // setTrack does not renegotiate, so the session stays untouched.
            val source = factory.createAudioSource(MediaConstraints())
            val newTrack = factory.createAudioTrack(MICROPHONE_TRACK_ID, source)
            audioSource = source
            microphoneTrack = newTrack
            audioTransceiver.sender.setTrack(newTrack, false)
            newTrack
        }
        track.setEnabled(true)
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
        // Disposal order matters: peer connection (closes transports and its receivers/senders,
        // which releases the platform audio capture), then our local track wrapper, then its
        // source
        peerConnection.dispose()
        microphoneTrack?.dispose()
        microphoneTrack = null
        audioSource?.dispose()
        audioSource = null
        eventsChannel.close()
    }
}

/**
 * Whether the answerer accepts audio from us: the direction attribute of its audio m-section
 * must be `recvonly` or `sendrecv` (the default when absent, per RFC 3264). No audio m-section
 * means the camera has no audio support at all.
 *
 * Internal for testing, the production entry point is [LibWebRtcPeerConnectionController.setAnswer].
 */
internal fun String.answerAcceptsClientAudio(): Boolean {
    var inAudioSection = false
    for (rawLine in lineSequence()) {
        val line = rawLine.trim()
        if (line.startsWith("m=")) {
            if (inAudioSection) break
            inAudioSection = line.startsWith("m=audio")
        } else if (inAudioSection) {
            when (line) {
                "a=recvonly", "a=sendrecv" -> return true
                "a=sendonly", "a=inactive" -> return false
            }
        }
    }
    // The direction defaults to sendrecv when the audio section has no direction attribute
    return inAudioSection
}

/**
 * Map the standardized stats report to the debugging snapshot. Only the nominated candidate pair
 * and the inbound/outbound RTP streams are extracted.
 */
private fun RTCStatsReport.toDebugStats(): RtcDebugStats {
    val stats = statsMap.values
    val inboundVideo = stats.firstOrNull { it.type == "inbound-rtp" && it.members["kind"] == "video" }
    val inboundAudio = stats.firstOrNull { it.type == "inbound-rtp" && it.members["kind"] == "audio" }
    val outboundAudio = stats.firstOrNull { it.type == "outbound-rtp" && it.members["kind"] == "audio" }
    val candidatePair = stats.firstOrNull {
        it.type == "candidate-pair" && it.members["state"] == "succeeded" && it.members["nominated"] == true
    }
    val videoCodec = inboundVideo?.members?.get("codecId")?.let { codecId ->
        statsMap[codecId]?.members?.get("mimeType") as? String
    }
    return RtcDebugStats(
        videoCodec = videoCodec,
        frameWidth = inboundVideo?.member("frameWidth"),
        frameHeight = inboundVideo?.member("frameHeight"),
        framesPerSecond = inboundVideo?.member("framesPerSecond"),
        framesDecoded = inboundVideo?.member("framesDecoded"),
        videoBytesReceived = inboundVideo?.member("bytesReceived"),
        videoPacketsLost = inboundVideo?.member("packetsLost"),
        audioBytesReceived = inboundAudio?.member("bytesReceived"),
        audioBytesSent = outboundAudio?.member("bytesSent"),
        roundTripTimeMs = candidatePair?.member<Double>("currentRoundTripTime")?.let { it * 1000 },
        localCandidateType = candidatePair?.relatedCandidateMember(this, "localCandidateId", "candidateType"),
        remoteCandidateType = candidatePair?.relatedCandidateMember(this, "remoteCandidateId", "candidateType"),
        transportProtocol = candidatePair?.relatedCandidateMember(this, "localCandidateId", "protocol"),
    )
}

/** Read a numeric stats member as [T], tolerating the varying boxed number types of libwebrtc. */
private inline fun <reified T> RTCStats.member(key: String): T? {
    val value = members[key] ?: return null
    return when (T::class) {
        Long::class -> (value as? Number)?.toLong() as? T
        Double::class -> (value as? Number)?.toDouble() as? T
        else -> value as? T
    }
}

/** Follow a candidate reference of a candidate-pair and read one member of the candidate. */
private fun RTCStats.relatedCandidateMember(report: RTCStatsReport, referenceKey: String, memberKey: String): String? =
    (members[referenceKey] as? String)?.let { candidateId ->
        report.statsMap[candidateId]?.members?.get(memberKey) as? String
    }

private fun MediaOptions.AudioDirection.toTransceiverDirection(): RtpTransceiver.RtpTransceiverDirection = when (this) {
    MediaOptions.AudioDirection.SEND_RECEIVE -> RtpTransceiver.RtpTransceiverDirection.SEND_RECV
    MediaOptions.AudioDirection.RECEIVE_ONLY -> RtpTransceiver.RtpTransceiverDirection.RECV_ONLY
    MediaOptions.AudioDirection.SEND_ONLY -> RtpTransceiver.RtpTransceiverDirection.SEND_ONLY
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
