package io.homeassistant.companion.android.webrtc.core.session.libwebrtc

import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import livekit.org.webrtc.RTCStats
import livekit.org.webrtc.RTCStatsReport
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class RtcStatsReportParserTest {

    @Test
    fun `Given a stats report Then the selected pair and inbound video are parsed`() {
        val report = RTCStatsReport(
            2_000_000L,
            mapOf(
                "codec0" to RTCStats(0L, "codec", "codec0", mapOf<String, Any>("mimeType" to "video/H264")),
                "cp0" to RTCStats(
                    0L,
                    "candidate-pair",
                    "cp0",
                    mapOf<String, Any>(
                        "nominated" to true,
                        "currentRoundTripTime" to 0.05,
                        "localCandidateId" to "lc0",
                        "remoteCandidateId" to "rc0",
                    ),
                ),
                "lc0" to RTCStats(0L, "local-candidate", "lc0", mapOf<String, Any>("candidateType" to "host")),
                "rc0" to RTCStats(0L, "remote-candidate", "rc0", mapOf<String, Any>("candidateType" to "srflx")),
                "v0" to RTCStats(
                    0L,
                    "inbound-rtp",
                    "v0",
                    mapOf<String, Any>(
                        "kind" to "video",
                        "codecId" to "codec0",
                        "bytesReceived" to 1_000L,
                        "frameWidth" to 1280L,
                        "frameHeight" to 720L,
                        "framesPerSecond" to 30.0,
                        "packetsLost" to 2L,
                    ),
                ),
            ),
        )

        val snapshot = report.toStatsSnapshot()

        assertEquals(Instant.fromEpochMilliseconds(2000), snapshot.timestamp)
        assertEquals(0.05.seconds, snapshot.candidatePair?.currentRtt)
        assertEquals("host", snapshot.candidatePair?.localCandidateType)
        assertEquals("srflx", snapshot.candidatePair?.remoteCandidateType)
        assertEquals("video/H264", snapshot.inboundVideo?.codec)
        assertEquals(1_000L, snapshot.inboundVideo?.bytes)
        assertEquals(1280, snapshot.inboundVideo?.frameWidth)
        assertEquals(720, snapshot.inboundVideo?.frameHeight)
        assertEquals(30, snapshot.inboundVideo?.fps)
        assertEquals(2, snapshot.inboundVideo?.packetsLost)
        assertNull(snapshot.inboundAudio)
        assertNull(snapshot.outboundAudio)
    }

    @Test
    fun `Given an empty stats report Then no pair or streams are present`() {
        val snapshot = RTCStatsReport(0L, emptyMap()).toStatsSnapshot()

        assertNull(snapshot.candidatePair)
        assertNull(snapshot.inboundVideo)
        assertNull(snapshot.inboundAudio)
        assertNull(snapshot.outboundAudio)
    }
}
