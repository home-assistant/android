package io.homeassistant.companion.android.webrtc.core.session

import io.homeassistant.companion.android.webrtc.core.MediaOptions
import io.homeassistant.companion.android.webrtc.core.RtcStatsSnapshot
import io.homeassistant.companion.android.webrtc.core.signaling.IceCandidateInit
import io.homeassistant.companion.android.webrtc.core.signaling.RtcClientConfig
import io.homeassistant.companion.android.webrtc.core.signaling.SignalingClient
import io.homeassistant.companion.android.webrtc.core.signaling.SignalingEvent
import io.homeassistant.companion.android.webrtc.core.signaling.SignalingResult
import io.homeassistant.companion.android.webrtc.core.signaling.WebRtcSessionId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.receiveAsFlow
import livekit.org.webrtc.VideoSink

internal class FakeSignalingClient : SignalingClient {

    var clientConfig = RtcClientConfig()
    var clientConfigFailure: SignalingResult.Failure? = null
    val sessionChannels = mutableListOf<Channel<SignalingEvent>>()
    val sentCandidates = mutableListOf<Pair<WebRtcSessionId, IceCandidateInit>>()
    var sendCandidateResult = true
    var openSessionCount = 0
    var activeSessionCount = 0
    var lastOfferSdp: String? = null

    /** The channel feeding the most recently opened session subscription. */
    val currentSession: Channel<SignalingEvent>
        get() = sessionChannels.last()

    override suspend fun getClientConfig(): SignalingResult<RtcClientConfig> = clientConfigFailure ?: SignalingResult.Success(clientConfig)

    override fun openSession(offerSdp: String): Flow<SignalingEvent> = flow {
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
        sessionId: WebRtcSessionId,
        candidate: IceCandidateInit,
    ): Boolean {
        sentCandidates += sessionId to candidate
        return sendCandidateResult
    }
}

internal class FakePeerConnectionController(
    private val offerSdp: String,
    val mediaOptions: MediaOptions = MediaOptions(),
) : PeerConnectionController {

    private val eventsChannel = Channel<PeerConnectionEvent>(Channel.UNLIMITED)
    override val events: Flow<PeerConnectionEvent> = eventsChannel.receiveAsFlow()

    val appliedAnswers = mutableListOf<String>()
    val remoteCandidates = mutableListOf<IceCandidateInit>()
    val sinks = mutableSetOf<VideoSink>()
    var microphoneEnabled: Boolean? = null
    var remoteAudioEnabled: Boolean? = null
    var disposed = false
    var createOfferException: Exception? = null

    /** When set, [dispose] suspends on it, so a test can observe teardown in progress. */
    var disposeGate: CompletableDeferred<Unit>? = null

    /** Supplies the snapshot returned by each [getStats] call; override to script a sequence. */
    var statsProvider: () -> RtcStatsSnapshot = { RtcStatsSnapshot.EMPTY }

    fun emit(event: PeerConnectionEvent) {
        eventsChannel.trySend(event)
    }

    override suspend fun createOffer(): String {
        createOfferException?.let { throw it }
        return offerSdp
    }

    override suspend fun setAnswer(sdp: String) {
        appliedAnswers += sdp
    }

    override suspend fun addRemoteCandidate(candidate: IceCandidateInit): Boolean {
        remoteCandidates += candidate
        return true
    }

    override suspend fun setMicrophoneEnabled(enabled: Boolean) {
        microphoneEnabled = enabled
    }

    override suspend fun setRemoteAudioEnabled(enabled: Boolean) {
        remoteAudioEnabled = enabled
    }

    override suspend fun addVideoSink(sink: VideoSink) {
        sinks += sink
    }

    override suspend fun removeVideoSink(sink: VideoSink) {
        sinks -= sink
    }

    override suspend fun getStats(): RtcStatsSnapshot = statsProvider()

    override suspend fun dispose() {
        disposeGate?.await()
        disposed = true
        eventsChannel.close()
    }
}

internal class FakePeerConnectionControllerFactory : PeerConnectionController.Factory {

    val controllers = mutableListOf<FakePeerConnectionController>()
    var createException: Exception? = null

    val lastController: FakePeerConnectionController
        get() = controllers.last()

    override suspend fun create(config: RtcClientConfig, mediaOptions: MediaOptions): PeerConnectionController {
        createException?.let { throw it }
        return FakePeerConnectionController(
            offerSdp = "v=0 fake-offer-${controllers.size}",
            mediaOptions = mediaOptions,
        ).also {
            controllers += it
        }
    }
}
