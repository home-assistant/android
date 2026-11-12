package io.homeassistant.companion.android.webrtc.fakes

import io.homeassistant.companion.android.webrtc.core.MediaOptions
import io.homeassistant.companion.android.webrtc.core.RtcStatsSnapshot
import io.homeassistant.companion.android.webrtc.core.session.PeerConnectionController
import io.homeassistant.companion.android.webrtc.core.session.PeerConnectionEvent
import io.homeassistant.companion.android.webrtc.core.signaling.IceCandidateInit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import livekit.org.webrtc.VideoSink

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
