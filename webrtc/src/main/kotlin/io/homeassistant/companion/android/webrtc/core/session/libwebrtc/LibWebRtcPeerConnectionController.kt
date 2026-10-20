package io.homeassistant.companion.android.webrtc.core.session.libwebrtc

import io.homeassistant.companion.android.webrtc.core.MediaOptions
import io.homeassistant.companion.android.webrtc.core.RtcStatsSnapshot
import io.homeassistant.companion.android.webrtc.core.session.PeerConnectionController
import io.homeassistant.companion.android.webrtc.core.session.PeerConnectionEvent
import io.homeassistant.companion.android.webrtc.core.signaling.IceCandidateInit
import io.homeassistant.companion.android.webrtc.core.signaling.RtcClientConfig
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

// Together these form the microphone m-section's `a=msid:<stream-id> <track-id>` attribute (RFC 8830),
// here `a=msid:ha_android ha_microphone`. go2rtc keys on the presence of a
// stream id, not its value, so this one just identifies the Android client. Both are valid msid tokens
// (1-64 token-chars, per the RFC 8830 / RFC 4566 grammar).
private const val MICROPHONE_TRACK_ID = "ha_microphone"
private const val MICROPHONE_STREAM_ID = "ha_android"

/**
 * [SdpObserver] with no-op defaults so callers only override the callbacks of the operation they
 * perform (create or set).
 */
private open class SdpObserverAdapter : SdpObserver {
    override fun onCreateSuccess(description: SessionDescription?) = Unit
    override fun onSetSuccess() = Unit
    override fun onCreateFailure(error: String?) = Unit
    override fun onSetFailure(error: String?) = Unit
}

/**
 * [PeerConnectionController] implementation backed by libwebrtc.
 *
 * The transceivers mirror the requested [MediaOptions]: an optional receive-only video
 * transceiver and an audio transceiver whose direction follows the receive/send audio flags. The
 * microphone track starts disabled so talk-back can be toggled without renegotiating.
 */
internal class LibWebRtcPeerConnectionController(
    factory: PeerConnectionFactory,
    config: RtcClientConfig,
    mediaOptions: MediaOptions,
) : PeerConnectionController {

    private val eventsChannel = Channel<PeerConnectionEvent>(Channel.UNLIMITED)
    override val events: Flow<PeerConnectionEvent> = eventsChannel.receiveAsFlow()

    // Sinks the consumer attached, retained so onTrack can bind them once the remote track arrives.
    // Writers (add/removeVideoSink, dispose) hold [mutex], but onTrack iterates this on libwebrtc's
    // signaling thread without the lock. CopyOnWriteArraySet is what lets that happen safely: onTrack
    // iterates a stable snapshot while a sink is concurrently added or removed, with no lock (and no
    // ConcurrentModificationException) on the callback path. A plain set would need onTrack to lock.
    private val attachedSinks = CopyOnWriteArraySet<VideoSink>()

    // Flipped once under [mutex] by dispose() and read under the lock by every guarded operation, but
    // also read lock-free by getStats(), which runs off [mutex]. Atomic so that off-lock read always
    // observes the flip rather than a stale value.
    private val disposed = AtomicBoolean(false)

    // Serializes the native-touching operations (add/removeVideoSink, set*Enabled, addRemoteCandidate)
    // against dispose(), so none of them touches the peer connection while it is being torn down. The
    // libwebrtc callbacks (onTrack) and getStats() deliberately stay off this lock and rely on the
    // volatile fields and the atomics instead, so a native callback never blocks on app code.
    private val mutex = Mutex()

    // Hand-off for the in-flight getStats continuation between getStats() (off [mutex]), dispose(),
    // and the libwebrtc stats callback. The CAS on this reference guarantees exactly one resume, so a
    // dispose() racing a stats request completes it with EMPTY instead of leaving the flow stalled.
    private val statsContinuation = AtomicReference<CancellableContinuation<RtcStatsSnapshot>?>()

    // Remote tracks assigned on libwebrtc's signaling thread in onTrack (off [mutex]) and cleared by
    // dispose() (under [mutex]); read from both sides. @Volatile publishes those cross-thread writes
    // without forcing the onTrack callback to take [mutex].
    @Volatile
    private var remoteVideoTrack: VideoTrack? = null

    @Volatile
    private var remoteAudioTrack: AudioTrack? = null

    // Desired remote-audio playback state. Written by setRemoteAudioEnabled under [mutex]; read by
    // onTrack (off [mutex]) to apply it to an audio track that arrives later. @Volatile for that read.
    @Volatile
    private var remoteAudioEnabled = true

    // libwebrtc's PeerConnection.Observer is a fat SDK interface: only three callbacks carry
    // behavior, the rest are mandatory no-op overrides, so the function count is inherent here.
    @Suppress("TooManyFunctions")
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
                    attachedSinks.forEach(track::addSink)
                }

                is AudioTrack -> {
                    remoteAudioTrack = track
                    track.setEnabled(remoteAudioEnabled)
                }

                else -> Unit
            }
        }

        override fun onSignalingChange(newState: PeerConnection.SignalingState?) = Unit
        override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState?) = Unit
        override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit
        override fun onIceGatheringChange(newState: PeerConnection.IceGatheringState?) = Unit
        override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) = Unit
        override fun onAddStream(stream: MediaStream?) = Unit
        override fun onRemoveStream(stream: MediaStream?) = Unit
        override fun onDataChannel(dataChannel: DataChannel?) = Unit
        override fun onRenegotiationNeeded() = Unit
        override fun onAddTrack(receiver: RtpReceiver?, mediaStreams: Array<out MediaStream>?) = Unit
    }

    private val peerConnection = checkNotNull(factory.createPeerConnection(config.toRtcConfiguration(), observer)) {
        "PeerConnection could not be created"
    }

    // The microphone capture chain only exists when sending audio was negotiated, so no recording
    // happens (and no RECORD_AUDIO permission is needed) otherwise.
    private val audioSource = if (mediaOptions.audioOut) factory.createAudioSource(MediaConstraints()) else null
    private val microphoneTrack = audioSource?.let {
        factory.createAudioTrack(MICROPHONE_TRACK_ID, it).apply { setEnabled(false) }
    }
    private var dataChannel: DataChannel? = config.dataChannelLabel?.let { label ->
        peerConnection.createDataChannel(label, DataChannel.Init())
    }

    init {
        if (mediaOptions.videoIn) {
            peerConnection.addTransceiver(
                MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO,
                RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.RECV_ONLY),
            )
        }
        addAudioTransceiver(mediaOptions)
    }

    private fun addAudioTransceiver(mediaOptions: MediaOptions) {
        val direction = mediaOptions.audioTransceiverDirection() ?: return
        if (mediaOptions.audioOut) {
            val transceiverInit = RtpTransceiver.RtpTransceiverInit(direction, listOf(MICROPHONE_STREAM_ID))
            peerConnection.addTransceiver(checkNotNull(microphoneTrack), transceiverInit)
        } else {
            val transceiverInit = RtpTransceiver.RtpTransceiverInit(direction)
            peerConnection.addTransceiver(MediaStreamTrack.MediaType.MEDIA_TYPE_AUDIO, transceiverInit)
        }
    }

    override suspend fun createOffer(): String {
        val offer = suspendCancellableCoroutine { continuation ->
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
        suspendCancellableCoroutine { continuation ->
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
        suspendCancellableCoroutine { continuation ->
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

    override suspend fun addRemoteCandidate(candidate: IceCandidateInit): Boolean = mutex.withLock {
        if (disposed.get()) return@withLock false
        peerConnection.addIceCandidate(candidate.toIceCandidate())
    }

    override suspend fun setMicrophoneEnabled(enabled: Boolean): Unit = mutex.withLock {
        if (disposed.get()) return@withLock
        microphoneTrack?.setEnabled(enabled)
    }

    override suspend fun setRemoteAudioEnabled(enabled: Boolean): Unit = mutex.withLock {
        if (disposed.get()) return@withLock
        remoteAudioEnabled = enabled
        remoteAudioTrack?.setEnabled(enabled)
    }

    override suspend fun addVideoSink(sink: VideoSink): Unit = mutex.withLock {
        if (disposed.get()) return@withLock
        attachedSinks.add(sink)
        remoteVideoTrack?.addSink(sink)
    }

    override suspend fun removeVideoSink(sink: VideoSink): Unit = mutex.withLock {
        if (disposed.get()) return@withLock
        attachedSinks.remove(sink)
        remoteVideoTrack?.removeSink(sink)
    }

    override suspend fun getStats(): RtcStatsSnapshot = suspendCancellableCoroutine { continuation ->
        statsContinuation.set(continuation)
        continuation.invokeOnCancellation { statsContinuation.compareAndSet(continuation, null) }
        // Re-check after publishing the continuation so a dispose() that raced the entry either
        // resumes it (via statsContinuation) or is observed here; either way the disposed peer
        // connection is never queried.
        if (disposed.get()) {
            if (statsContinuation.compareAndSet(continuation, null)) continuation.resume(RtcStatsSnapshot.EMPTY)
        } else {
            peerConnection.getStats { report ->
                if (statsContinuation.compareAndSet(continuation, null)) {
                    continuation.resume(report.toStatsSnapshot())
                }
            }
        }
    }

    override suspend fun dispose(): Unit = mutex.withLock {
        if (disposed.getAndSet(true)) return@withLock
        statsContinuation.getAndSet(null)?.resume(RtcStatsSnapshot.EMPTY)
        remoteVideoTrack?.let { track ->
            attachedSinks.forEach { sink ->
                runCatching { track.removeSink(sink) }
                    .onFailure { Timber.w(it, "Failed to detach a video sink during disposal") }
            }
        }
        attachedSinks.clear()
        // The remote tracks are owned by the peer connection and disposed with it
        remoteVideoTrack = null
        remoteAudioTrack = null
        dataChannel?.dispose()
        dataChannel = null
        // Disposal order matters: peer connection (closes transports and its receivers/senders),
        // then our local track wrapper, then its source
        peerConnection.dispose()
        microphoneTrack?.dispose()
        audioSource?.dispose()
        eventsChannel.close()
    }
}
