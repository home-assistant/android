package io.homeassistant.companion.android.webrtc.core.session.libwebrtc

import io.homeassistant.companion.android.webrtc.core.MediaOptions
import io.homeassistant.companion.android.webrtc.core.RtcStatsSnapshot
import io.homeassistant.companion.android.webrtc.core.session.PeerConnectionController
import io.homeassistant.companion.android.webrtc.core.session.PeerConnectionEvent
import io.homeassistant.companion.android.webrtc.core.signaling.IceCandidateInit
import io.homeassistant.companion.android.webrtc.core.signaling.RtcClientConfig
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
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
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : PeerConnectionController {

    private val eventsChannel = Channel<PeerConnectionEvent>(Channel.UNLIMITED)
    override val events: Flow<PeerConnectionEvent> = eventsChannel.receiveAsFlow()

    // Sinks the consumer attached, retained so syncRemoteTracks can bind them once the remote track
    // arrives. Every access (add/removeVideoSink, syncRemoteTracks, dispose) holds [mutex], so a plain
    // set is enough: onTrack no longer iterates it off the lock, it only hands the track over.
    private val attachedSinks = mutableSetOf<VideoSink>()

    // Flipped once under [mutex] by dispose() and read under the lock by every guarded operation, but
    // also read lock-free by getStats() before it takes [mutex]. Atomic so that the off-lock read
    // always observes the flip rather than a stale value.
    private val disposed = AtomicBoolean(false)

    // Serializes every native-touching operation (add/removeVideoSink, syncRemoteTracks, set*Enabled,
    // addRemoteCandidate, getStats submission) against dispose(), so none of them touches the peer
    // connection while it is being torn down. The libwebrtc callbacks (onTrack, the stats callback)
    // stay off this lock so a native callback never blocks on app code: onTrack only publishes the
    // arrived track and signals, and the stats callback only completes a deferred.
    private val mutex = Mutex()

    // Runs the track binding triggered by onTrack off the native callback thread and under [mutex],
    // so it is serialized with add/removeVideoSink and dispose() without the signaling thread ever
    // blocking on app code. Owned by this controller and cancelled in dispose().
    private val bindingScope = CoroutineScope(SupervisorJob() + dispatcher)

    // In-flight getStats requests, each with its own deferred so concurrent collectors never clobber
    // one another. Added and drained under [mutex]; dispose() completes any outstanding ones with
    // EMPTY so a stats poll racing teardown never stalls.
    private val pendingStats = mutableSetOf<CompletableDeferred<RtcStatsSnapshot>>()

    // The remote tracks, bound to the attached sinks / playback state once they arrive. Written and
    // read only under [mutex] (bindRemoteTrack, the sink operations, setRemoteAudioEnabled, dispose),
    // so they need no further synchronization.
    private var remoteVideoTrack: VideoTrack? = null
    private var remoteAudioTrack: AudioTrack? = null

    // Desired remote-audio playback state, written and read under [mutex] and applied to the audio
    // track by setRemoteAudioEnabled or, for a track that arrives later, by bindRemoteTrack.
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
            // Runs on libwebrtc's signaling thread: capture the track and defer the binding to
            // [bindingScope] so it runs under [mutex], serialized with the sink operations and
            // dispose(), without the signaling thread ever blocking on app code.
            val track = transceiver?.receiver?.track() ?: return
            bindingScope.launch { bindRemoteTrack(track) }
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

    private suspend fun bindRemoteTrack(track: MediaStreamTrack): Unit = mutex.withLock {
        if (disposed.get()) return@withLock
        when (track) {
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

    override suspend fun getStats(): RtcStatsSnapshot {
        val result = CompletableDeferred<RtcStatsSnapshot>()
        mutex.withLock {
            // The disposed check and the native submission share [mutex] with dispose(), so the peer
            // connection can never be released between the two. The callback fires off the lock.
            if (disposed.get()) return RtcStatsSnapshot.EMPTY
            pendingStats.add(result)
            peerConnection.getStats { report -> result.complete(report.toStatsSnapshot()) }
        }
        return try {
            result.await()
        } finally {
            // Stop tracking the request once it completed or the collector was cancelled.
            withContext(NonCancellable) { mutex.withLock { pendingStats.remove(result) } }
        }
    }

    override suspend fun dispose(): Unit = mutex.withLock {
        if (disposed.getAndSet(true)) return@withLock
        // Stop any pending/ongoing track binding; a sync already waiting on [mutex] will observe
        // the disposed flag and no-op.
        bindingScope.cancel()
        // Complete any in-flight stats poll so its collector resumes instead of awaiting forever.
        pendingStats.forEach { it.complete(RtcStatsSnapshot.EMPTY) }
        pendingStats.clear()
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
