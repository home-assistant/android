package io.homeassistant.companion.android.webrtc.core.session

import io.homeassistant.companion.android.webrtc.core.MediaOptions
import io.homeassistant.companion.android.webrtc.core.audio.AudioController
import io.homeassistant.companion.android.webrtc.core.signaling.IceCandidateInit
import io.homeassistant.companion.android.webrtc.core.signaling.RtcClientConfig
import io.homeassistant.companion.android.webrtc.core.signaling.SignalingClient
import io.homeassistant.companion.android.webrtc.core.signaling.SignalingEvent
import io.homeassistant.companion.android.webrtc.core.signaling.SignalingException
import io.homeassistant.companion.android.webrtc.core.signaling.StreamType
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.receiveAsFlow
import livekit.org.webrtc.VideoSink

internal class FakeSignalingClient : SignalingClient {

    var clientConfig = RtcClientConfig()
    var clientConfigException: SignalingException? = null
    val sessionChannels = mutableListOf<Channel<SignalingEvent>>()
    val sentCandidates = mutableListOf<Pair<String, IceCandidateInit>>()
    var sendCandidateResult = true
    var openSessionCount = 0
    var activeSessionCount = 0
    var lastOfferSdp: String? = null

    /** The channel feeding the most recently opened session subscription. */
    val currentSession: Channel<SignalingEvent>
        get() = sessionChannels.last()

    override suspend fun getStreamCapabilities(entityId: String): Set<StreamType> = setOf(StreamType.HLS, StreamType.WEB_RTC)

    override suspend fun getClientConfig(entityId: String): RtcClientConfig {
        clientConfigException?.let { throw it }
        return clientConfig
    }

    override fun openSession(entityId: String, offerSdp: String): Flow<SignalingEvent> = flow {
        openSessionCount++
        lastOfferSdp = offerSdp
        val channel = Channel<SignalingEvent>(Channel.UNLIMITED)
        sessionChannels += channel
        activeSessionCount++
        try {
            emitAll(channel.receiveAsFlow())
        } finally {
            activeSessionCount--
        }
    }

    override suspend fun sendCandidate(
        entityId: String,
        sessionId: String,
        candidate: IceCandidateInit,
    ): Boolean {
        sentCandidates += sessionId to candidate
        return sendCandidateResult
    }
}

internal class FakePeerConnectionController(private val offerSdp: String) : PeerConnectionController {

    private val eventsChannel = Channel<PeerConnectionEvent>(Channel.UNLIMITED)
    override val events: Flow<PeerConnectionEvent> = eventsChannel.receiveAsFlow()

    val appliedAnswers = mutableListOf<String>()
    val remoteCandidates = mutableListOf<IceCandidateInit>()
    val sinks = mutableSetOf<VideoSink>()
    var microphoneEnabled: Boolean? = null
    var remoteAudioEnabled: Boolean? = null
    var disposed = false
    var createOfferException: Exception? = null
    var debugStats: RtcDebugStats? = null

    override var isMicrophoneSupported = true

    fun emit(event: PeerConnectionEvent) {
        eventsChannel.trySend(event)
    }

    override suspend fun getStats(): RtcDebugStats? = debugStats

    override suspend fun createOffer(): String {
        createOfferException?.let { throw it }
        return offerSdp
    }

    override suspend fun setAnswer(sdp: String) {
        appliedAnswers += sdp
    }

    override fun addRemoteCandidate(candidate: IceCandidateInit): Boolean {
        remoteCandidates += candidate
        return true
    }

    override fun setMicrophoneEnabled(enabled: Boolean) {
        microphoneEnabled = enabled
    }

    override fun setRemoteAudioEnabled(enabled: Boolean) {
        remoteAudioEnabled = enabled
    }

    override fun addVideoSink(sink: VideoSink) {
        sinks += sink
    }

    override fun removeVideoSink(sink: VideoSink) {
        sinks -= sink
    }

    override fun dispose() {
        disposed = true
        eventsChannel.close()
    }
}

internal class FakeAudioController : AudioController {

    var acquireCount = 0
    var releaseCount = 0

    /** How many acquisitions are currently unbalanced by a release. */
    val activeHolds: Int
        get() = acquireCount - releaseCount

    override fun acquire() {
        acquireCount++
    }

    override fun release() {
        releaseCount++
    }
}

internal class FakePeerConnectionControllerFactory : PeerConnectionController.Factory {

    val controllers = mutableListOf<FakePeerConnectionController>()
    var createException: Exception? = null
    var micSupported = true
    var lastMediaOptions: MediaOptions? = null

    val lastController: FakePeerConnectionController
        get() = controllers.last()

    override fun create(config: RtcClientConfig, mediaOptions: MediaOptions): PeerConnectionController {
        createException?.let { throw it }
        lastMediaOptions = mediaOptions
        return FakePeerConnectionController(offerSdp = "v=0 fake-offer-${controllers.size}").also {
            // Mirrors the real controller: a receive-only negotiation can never send
            it.isMicrophoneSupported = micSupported &&
                mediaOptions.audio != MediaOptions.AudioDirection.RECEIVE_ONLY
            controllers += it
        }
    }
}
