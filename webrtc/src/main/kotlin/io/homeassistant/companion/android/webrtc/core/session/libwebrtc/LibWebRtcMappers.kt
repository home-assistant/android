package io.homeassistant.companion.android.webrtc.core.session.libwebrtc

import io.homeassistant.companion.android.webrtc.core.MediaOptions
import io.homeassistant.companion.android.webrtc.core.session.RtcConnectionState
import io.homeassistant.companion.android.webrtc.core.signaling.IceCandidateInit
import io.homeassistant.companion.android.webrtc.core.signaling.RtcClientConfig
import livekit.org.webrtc.IceCandidate
import livekit.org.webrtc.PeerConnection
import livekit.org.webrtc.RtpTransceiver.RtpTransceiverDirection

private const val FIRST_MEDIA_SECTION_MID = "0"

/**
 * The audio transceiver direction to negotiate for the requested [MediaOptions], or `null` when no
 * audio transceiver should be added at all (neither receiving nor sending audio). `null` is distinct
 * from [RtpTransceiverDirection.INACTIVE], which would still negotiate an audio m-section.
 */
internal fun MediaOptions.audioTransceiverDirection(): RtpTransceiverDirection? = when {
    audioIn && audioOut -> RtpTransceiverDirection.SEND_RECV
    audioOut -> RtpTransceiverDirection.SEND_ONLY
    audioIn -> RtpTransceiverDirection.RECV_ONLY
    else -> null
}

internal fun RtcClientConfig.toRtcConfiguration(): PeerConnection.RTCConfiguration {
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

internal fun PeerConnection.PeerConnectionState.toRtcConnectionState(): RtcConnectionState = when (this) {
    PeerConnection.PeerConnectionState.NEW -> RtcConnectionState.NEW
    PeerConnection.PeerConnectionState.CONNECTING -> RtcConnectionState.CONNECTING
    PeerConnection.PeerConnectionState.CONNECTED -> RtcConnectionState.CONNECTED
    PeerConnection.PeerConnectionState.DISCONNECTED -> RtcConnectionState.DISCONNECTED
    PeerConnection.PeerConnectionState.FAILED -> RtcConnectionState.FAILED
    PeerConnection.PeerConnectionState.CLOSED -> RtcConnectionState.CLOSED
}

/**
 * Converts to the libwebrtc [IceCandidate]. libwebrtc needs a non-null sdpMid; when the signaling
 * omits it the m-line index resolves the section, and when neither is provided the first m-section
 * is targeted, matching the iOS and frontend clients.
 */
internal fun IceCandidateInit.toIceCandidate(): IceCandidate = IceCandidate(
    sdpMid ?: if (sdpMLineIndex == null) FIRST_MEDIA_SECTION_MID else "",
    sdpMLineIndex ?: 0,
    candidate,
)
