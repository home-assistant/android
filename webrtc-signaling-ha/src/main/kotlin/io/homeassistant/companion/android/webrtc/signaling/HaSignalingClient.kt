package io.homeassistant.companion.android.webrtc.signaling

import io.homeassistant.companion.android.common.data.websocket.WebSocketRepository
import io.homeassistant.companion.android.common.data.websocket.impl.entities.CameraStreamTypes
import io.homeassistant.companion.android.common.data.websocket.impl.entities.WebRtcCandidate
import io.homeassistant.companion.android.common.data.websocket.impl.entities.WebRtcEvent
import io.homeassistant.companion.android.webrtc.core.signaling.IceCandidateInit
import io.homeassistant.companion.android.webrtc.core.signaling.RtcClientConfig
import io.homeassistant.companion.android.webrtc.core.signaling.RtcIceServer
import io.homeassistant.companion.android.webrtc.core.signaling.SignalingClient
import io.homeassistant.companion.android.webrtc.core.signaling.SignalingEvent
import io.homeassistant.companion.android.webrtc.core.signaling.SignalingException
import io.homeassistant.companion.android.webrtc.core.signaling.StreamType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.mapNotNull
import timber.log.Timber

/**
 * [SignalingClient] implementation over the Home Assistant WebSocket API
 * (`camera/capabilities` and the `camera/webrtc` commands).
 *
 * The WebRTC signaling API requires Home Assistant Core 2024.12 or later; consumers should
 * check the server version and the camera capabilities before starting a session.
 *
 * @param webSocketRepository the WebSocket repository of the server the camera belongs to
 */
class HaSignalingClient(private val webSocketRepository: WebSocketRepository) : SignalingClient {

    override suspend fun getStreamCapabilities(entityId: String): Set<StreamType> {
        val capabilities = webSocketRepository.getCameraCapabilities(entityId)
            ?: throw SignalingException(message = "Unable to get the camera capabilities")
        return capabilities.frontendStreamTypes.mapNotNull { streamType ->
            when (streamType) {
                CameraStreamTypes.HLS -> StreamType.HLS
                CameraStreamTypes.WEB_RTC -> StreamType.WEB_RTC
                else -> {
                    Timber.d("Ignoring unknown camera stream type $streamType")
                    null
                }
            }
        }.toSet()
    }

    override suspend fun getClientConfig(entityId: String): RtcClientConfig {
        val config = webSocketRepository.getCameraWebRtcClientConfig(entityId)
            ?: throw SignalingException(message = "Unable to get the WebRTC client configuration")
        return RtcClientConfig(
            iceServers = config.configuration.iceServers.map { server ->
                RtcIceServer(urls = server.urls, username = server.username, credential = server.credential)
            },
            dataChannelLabel = config.dataChannel,
        )
    }

    override fun openSession(entityId: String, offerSdp: String): Flow<SignalingEvent> = flow {
        val events = webSocketRepository.startCameraWebRtcSession(entityId, offerSdp)
            ?: throw SignalingException(message = "The WebRTC session could not be started")
        emitAll(events.mapNotNull { it.toSignalingEvent() })
    }

    override suspend fun sendCandidate(entityId: String, sessionId: String, candidate: IceCandidateInit): Boolean =
        webSocketRepository.sendCameraWebRtcCandidate(
            entityId = entityId,
            sessionId = sessionId,
            candidate = WebRtcCandidate(
                candidate = candidate.candidate,
                sdpMid = candidate.sdpMid,
                sdpMLineIndex = candidate.sdpMLineIndex,
                usernameFragment = candidate.usernameFragment,
            ),
        )
}

private fun WebRtcEvent.toSignalingEvent(): SignalingEvent? = when (this) {
    is WebRtcEvent.Session -> SignalingEvent.Session(sessionId = sessionId)
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
