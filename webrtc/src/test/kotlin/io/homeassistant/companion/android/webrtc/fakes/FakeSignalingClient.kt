package io.homeassistant.companion.android.webrtc.fakes

import io.homeassistant.companion.android.webrtc.core.signaling.IceCandidateInit
import io.homeassistant.companion.android.webrtc.core.signaling.RtcClientConfig
import io.homeassistant.companion.android.webrtc.core.signaling.SignalingClient
import io.homeassistant.companion.android.webrtc.core.signaling.SignalingEvent
import io.homeassistant.companion.android.webrtc.core.signaling.SignalingResult
import io.homeassistant.companion.android.webrtc.core.signaling.WebRtcSessionId
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.receiveAsFlow

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
