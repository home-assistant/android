package io.homeassistant.companion.android.webrtc.core.session

/**
 * Snapshot of the most useful WebRTC statistics for debugging a session, mapped from the
 * standardized `RTCStatsReport` of the peer connection.
 *
 * All values are cumulative or instantaneous as defined by the W3C stats spec; rates (like
 * bitrates) must be derived by the caller from two consecutive snapshots. Every field is nullable
 * because its stats object may not exist yet (for example before the first frame arrived).
 */
data class RtcDebugStats(
    /** Mime type of the negotiated video codec, for example `video/H264`. */
    val videoCodec: String? = null,
    val frameWidth: Long? = null,
    val frameHeight: Long? = null,
    val framesPerSecond: Double? = null,
    val framesDecoded: Long? = null,
    val videoBytesReceived: Long? = null,
    val videoPacketsLost: Long? = null,
    val audioBytesReceived: Long? = null,
    /** Bytes sent on the microphone track, `null` or 0 while the microphone never went live. */
    val audioBytesSent: Long? = null,
    /** Current round trip time of the nominated candidate pair, in milliseconds. */
    val roundTripTimeMs: Double? = null,
    /** Candidate type of the local end of the nominated pair (`host`, `srflx`, `relay`...). */
    val localCandidateType: String? = null,
    /** Candidate type of the remote end of the nominated pair. */
    val remoteCandidateType: String? = null,
    /** Transport protocol of the nominated pair (`udp` or `tcp`). */
    val transportProtocol: String? = null,
)
