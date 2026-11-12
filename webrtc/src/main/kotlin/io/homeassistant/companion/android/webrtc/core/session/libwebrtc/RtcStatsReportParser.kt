package io.homeassistant.companion.android.webrtc.core.session.libwebrtc

import io.homeassistant.companion.android.webrtc.core.RtcStatsSnapshot
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import livekit.org.webrtc.RTCStats
import livekit.org.webrtc.RTCStatsReport

private const val MICROS_PER_MILLISECOND = 1000L

private const val STATS_TYPE_INBOUND_RTP = "inbound-rtp"
private const val STATS_TYPE_OUTBOUND_RTP = "outbound-rtp"
private const val STATS_TYPE_CANDIDATE_PAIR = "candidate-pair"
private const val STATS_TYPE_CODEC = "codec"
private const val CANDIDATE_TYPE_SUFFIX = "-candidate"
private const val MEMBER_KIND = "kind"
private const val KIND_VIDEO = "video"
private const val KIND_AUDIO = "audio"
private const val MEMBER_BYTES_RECEIVED = "bytesReceived"
private const val MEMBER_BYTES_SENT = "bytesSent"
private const val MEMBER_PACKETS_LOST = "packetsLost"
private const val MEMBER_FRAME_WIDTH = "frameWidth"
private const val MEMBER_FRAME_HEIGHT = "frameHeight"
private const val MEMBER_FRAMES_PER_SECOND = "framesPerSecond"
private const val MEMBER_CODEC_ID = "codecId"
private const val MEMBER_MIME_TYPE = "mimeType"
private const val MEMBER_NOMINATED = "nominated"
private const val MEMBER_STATE = "state"
private const val STATE_SUCCEEDED = "succeeded"
private const val MEMBER_CURRENT_RTT = "currentRoundTripTime"
private const val MEMBER_LOCAL_CANDIDATE_ID = "localCandidateId"
private const val MEMBER_REMOTE_CANDIDATE_ID = "remoteCandidateId"
private const val MEMBER_CANDIDATE_TYPE = "candidateType"

/**
 * Parses the subset of the libwebrtc [RTCStatsReport]: the selected ICE
 * candidate pair and the inbound video/audio and outbound audio RTP streams. Bitrates are left at
 * 0, the session derives them from consecutive snapshots.
 */
internal fun RTCStatsReport.toStatsSnapshot(): RtcStatsSnapshot {
    val stats = statsMap.values
    val codecMimeById = stats.filter { it.type == STATS_TYPE_CODEC }.associate { it.id to it.string(MEMBER_MIME_TYPE) }
    val candidatesById = stats.filter { it.type.endsWith(CANDIDATE_TYPE_SUFFIX) }.associateBy { it.id }
    return RtcStatsSnapshot(
        timestamp = Instant.fromEpochMilliseconds(timestampUs.toLong() / MICROS_PER_MILLISECOND),
        candidatePair = stats.selectedCandidatePair()?.toCandidatePairStats(candidatesById),
        inboundVideo = stats.firstRtp(STATS_TYPE_INBOUND_RTP, KIND_VIDEO)?.toInboundVideoStats(codecMimeById),
        inboundAudio = stats.firstRtp(
            STATS_TYPE_INBOUND_RTP,
            KIND_AUDIO,
        )?.toAudioStats(codecMimeById, MEMBER_BYTES_RECEIVED),
        outboundAudio = stats.firstRtp(
            STATS_TYPE_OUTBOUND_RTP,
            KIND_AUDIO,
        )?.toAudioStats(codecMimeById, MEMBER_BYTES_SENT),
    )
}

private fun Collection<RTCStats>.firstRtp(type: String, kind: String): RTCStats? =
    firstOrNull { it.type == type && it.string(MEMBER_KIND) == kind }

private fun Collection<RTCStats>.selectedCandidatePair(): RTCStats? =
    firstOrNull { it.type == STATS_TYPE_CANDIDATE_PAIR && it.members[MEMBER_NOMINATED] == true }
        ?: firstOrNull { it.type == STATS_TYPE_CANDIDATE_PAIR && it.string(MEMBER_STATE) == STATE_SUCCEEDED }

private fun RTCStats.toCandidatePairStats(candidatesById: Map<String, RTCStats>) = RtcStatsSnapshot.CandidatePairStats(
    currentRtt = number(MEMBER_CURRENT_RTT)?.let { it.toDouble().seconds },
    localCandidateType = candidatesById[string(MEMBER_LOCAL_CANDIDATE_ID)]?.string(MEMBER_CANDIDATE_TYPE),
    remoteCandidateType = candidatesById[string(MEMBER_REMOTE_CANDIDATE_ID)]?.string(MEMBER_CANDIDATE_TYPE),
)

private fun RTCStats.toInboundVideoStats(codecMimeById: Map<String, String?>) = RtcStatsSnapshot.RtpStreamStats(
    codec = codecMimeById[string(MEMBER_CODEC_ID)],
    bytes = number(MEMBER_BYTES_RECEIVED)?.toLong() ?: 0,
    packetsLost = number(MEMBER_PACKETS_LOST)?.toInt(),
    frameWidth = number(MEMBER_FRAME_WIDTH)?.toInt(),
    frameHeight = number(MEMBER_FRAME_HEIGHT)?.toInt(),
    fps = number(MEMBER_FRAMES_PER_SECOND)?.toDouble()?.roundToInt(),
)

private fun RTCStats.toAudioStats(codecMimeById: Map<String, String?>, bytesKey: String) =
    RtcStatsSnapshot.RtpStreamStats(
        codec = codecMimeById[string(MEMBER_CODEC_ID)],
        bytes = number(bytesKey)?.toLong() ?: 0,
        packetsLost = number(MEMBER_PACKETS_LOST)?.toInt(),
    )

private fun RTCStats.number(key: String): Number? = members[key] as? Number

private fun RTCStats.string(key: String): String? = members[key] as? String
