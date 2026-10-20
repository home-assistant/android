package io.homeassistant.companion.android.webrtc.signaling

import io.homeassistant.companion.android.common.data.websocket.WebSocketRepository
import io.homeassistant.companion.android.common.data.websocket.impl.entities.WebRtcCandidate
import io.homeassistant.companion.android.common.data.websocket.impl.entities.WebRtcEvent
import io.homeassistant.companion.android.webrtc.core.signaling.IceCandidateInit
import io.homeassistant.companion.android.webrtc.core.signaling.RtcClientConfig
import io.homeassistant.companion.android.webrtc.core.signaling.RtcIceServer
import io.homeassistant.companion.android.webrtc.core.signaling.SignalingClient
import io.homeassistant.companion.android.webrtc.core.signaling.SignalingEvent
import io.homeassistant.companion.android.webrtc.core.signaling.SignalingResult
import io.homeassistant.companion.android.webrtc.core.signaling.WebRtcSessionId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.mapNotNull
import timber.log.Timber

/**
 * [SignalingClient] implementation over the Home Assistant WebSocket API (the `camera/webrtc`
 * commands).
 *
 * The WebRTC signaling API requires Home Assistant Core 2024.12 or later; consumers should
 * check the server version and the camera capabilities before starting a session.
 *
 * @param webSocketRepository the WebSocket repository of the server the camera belongs to
 * @param entityId the camera entity this client signals for
 */
internal class HaSignalingClient(private val webSocketRepository: WebSocketRepository, private val entityId: String) :
    SignalingClient {

    override suspend fun getClientConfig(): SignalingResult<RtcClientConfig> {
        val config = webSocketRepository.getCameraWebRtcClientConfig(entityId)
            ?: return SignalingResult.Failure(
                code = "signaling_failed",
                message = "Unable to get the WebRTC client configuration",
            )
        return SignalingResult.Success(
            RtcClientConfig(
                iceServers = config.configuration.iceServers.map { server ->
                    RtcIceServer(urls = server.urls, username = server.username, credential = server.credential)
                },
                dataChannelLabel = config.dataChannel,
            ),
        )
    }

    override fun openSession(offerSdp: String): Flow<SignalingEvent> = flow {
        val events = webSocketRepository.startCameraWebRtcSession(entityId, offerSdp)
        if (events == null) {
            emit(SignalingEvent.Error(code = null, message = "The WebRTC session could not be started"))
            return@flow
        }
        emitAll(events.mapNotNull { it.toSignalingEvent() })
    }

    override suspend fun sendCandidate(sessionId: WebRtcSessionId, candidate: IceCandidateInit): Boolean =
        webSocketRepository.sendCameraWebRtcCandidate(
            entityId = entityId,
            sessionId = sessionId.value,
            candidate = WebRtcCandidate(
                candidate = candidate.candidate,
                sdpMid = candidate.sdpMid,
                sdpMLineIndex = candidate.sdpMLineIndex,
                usernameFragment = candidate.usernameFragment,
            ),
        )
}

private fun WebRtcEvent.toSignalingEvent(): SignalingEvent? = when (this) {
    is WebRtcEvent.Session -> SignalingEvent.Session(sessionId = WebRtcSessionId(sessionId))
    is WebRtcEvent.Answer -> SignalingEvent.Answer(sdp = answer)
    is WebRtcEvent.Candidate -> SignalingEvent.Candidate(
        candidate = IceCandidateInit(
            candidate = candidate.candidate,
            sdpMid = candidate.sdpMid,
            sdpMLineIndex = candidate.sdpMLineIndex,
            usernameFragment = candidate.usernameFragment,
        ),
    )
    is WebRtcEvent.Error -> SignalingEvent.Error(code = code, message = message)
    is WebRtcEvent.Unknown -> {
        Timber.d("Ignoring unknown WebRTC signaling event $discriminator")
        null
    }
}
