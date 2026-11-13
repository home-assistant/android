package io.homeassistant.companion.android.webrtc.core

import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.Instant

private const val BITS_PER_BYTE = 8
private const val BITS_PER_KILOBIT = 1000.0

/**
 * A point-in-time snapshot of the WebRTC transport and media statistics of a session, parsed from
 * the libwebrtc `RTCStatsReport`.
 *
 * Byte counts are cumulative as reported by libwebrtc; the per-second bitrates ([RtpStreamStats.kbps])
 * are derived from the delta between two consecutive snapshots and are therefore `0` on the raw
 * snapshot returned by a controller until enriched by the session.
 *
 * @property timestamp the report timestamp, used to derive bitrates
 * @property candidatePair the selected ICE candidate pair, or null when none is established yet
 * @property inboundVideo statistics of the received video stream, or null when not received
 * @property inboundAudio statistics of the received audio stream, or null when not received
 * @property outboundAudio statistics of the sent microphone stream, or null when not sending
 */
data class RtcStatsSnapshot(
    val timestamp: Instant = Instant.fromEpochSeconds(0),
    val candidatePair: CandidatePairStats? = null,
    val inboundVideo: RtpStreamStats? = null,
    val inboundAudio: RtpStreamStats? = null,
    val outboundAudio: RtpStreamStats? = null,
) {
    /**
     * Returns a copy of this snapshot with each stream's bitrate derived from the byte and timestamp
     * deltas against [previous]. Without a usable previous snapshot the bitrates stay at 0.
     */
    internal fun withRatesFrom(previous: RtcStatsSnapshot?): RtcStatsSnapshot {
        val elapsed = previous?.let { timestamp - it.timestamp } ?: Duration.ZERO
        return if (elapsed <= Duration.ZERO) {
            this
        } else {
            copy(
                inboundVideo = inboundVideo?.withKbps(previous?.inboundVideo, elapsed),
                inboundAudio = inboundAudio?.withKbps(previous?.inboundAudio, elapsed),
                outboundAudio = outboundAudio?.withKbps(previous?.outboundAudio, elapsed),
            )
        }
    }

    /** The selected ICE candidate pair carrying the media. */
    data class CandidatePairStats(
        val currentRtt: Duration? = null,
        val localCandidateType: String? = null,
        val remoteCandidateType: String? = null,
    )

    /**
     * Statistics of a single RTP stream (one direction of one media kind).
     *
     * @property codec the negotiated codec mime type, for example `video/H264`
     * @property bytes cumulative bytes received or sent on this stream
     * @property kbps bitrate over the last interval, derived by the session (0 on a raw snapshot)
     * @property packetsLost cumulative lost packets, when reported
     * @property frameWidth width of the last decoded frame, video only
     * @property frameHeight height of the last decoded frame, video only
     * @property fps frames per second, video only
     */
    data class RtpStreamStats(
        val codec: String? = null,
        val bytes: Long = 0,
        val kbps: Long = 0,
        val packetsLost: Int? = null,
        val frameWidth: Int? = null,
        val frameHeight: Int? = null,
        val fps: Int? = null,
    )

    companion object {
        /** An empty snapshot, used while no peer connection is established. */
        val EMPTY = RtcStatsSnapshot()
    }
}

private fun RtcStatsSnapshot.RtpStreamStats.withKbps(
    previous: RtcStatsSnapshot.RtpStreamStats?,
    elapsed: Duration,
): RtcStatsSnapshot.RtpStreamStats {
    val deltaBytes = previous?.let { bytes - it.bytes } ?: 0
    return if (deltaBytes <= 0) {
        this
    } else {
        copy(kbps = (deltaBytes * BITS_PER_BYTE / BITS_PER_KILOBIT / elapsed.toDouble(DurationUnit.SECONDS)).toLong())
    }
}
