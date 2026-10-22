package io.homeassistant.companion.android.webrtc.core

import kotlin.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class RtcStatsSnapshotTest {

    private val t0 = Instant.fromEpochSeconds(0)
    private val t1 = Instant.fromEpochSeconds(1)

    @Test
    fun `Given no previous snapshot When deriving rates Then the snapshot is returned unchanged`() {
        val snapshot = RtcStatsSnapshot(timestamp = t1, inboundVideo = RtcStatsSnapshot.RtpStreamStats(bytes = 25_000))

        assertSame(snapshot, snapshot.withRatesFrom(null))
    }

    @Test
    fun `Given the same timestamp When deriving rates Then the snapshot is returned unchanged`() {
        val previous = RtcStatsSnapshot(timestamp = t1, inboundVideo = RtcStatsSnapshot.RtpStreamStats(bytes = 0))
        val current = RtcStatsSnapshot(timestamp = t1, inboundVideo = RtcStatsSnapshot.RtpStreamStats(bytes = 25_000))

        assertSame(current, current.withRatesFrom(previous))
    }

    @Test
    fun `Given increasing bytes over one second When deriving rates Then kbps is the bitrate`() {
        val previous = RtcStatsSnapshot(timestamp = t0, inboundVideo = RtcStatsSnapshot.RtpStreamStats(bytes = 0))
        val current = RtcStatsSnapshot(timestamp = t1, inboundVideo = RtcStatsSnapshot.RtpStreamStats(bytes = 25_000))

        // 25_000 B * 8 bits / 1000 / 1 s
        assertEquals(200, current.withRatesFrom(previous).inboundVideo?.kbps)
    }

    @Test
    fun `Given a two second interval When deriving rates Then kbps accounts for the elapsed time`() {
        val previous = RtcStatsSnapshot(timestamp = t0, inboundAudio = RtcStatsSnapshot.RtpStreamStats(bytes = 0))
        val current = RtcStatsSnapshot(
            timestamp = Instant.fromEpochSeconds(2),
            inboundAudio = RtcStatsSnapshot.RtpStreamStats(bytes = 50_000),
        )

        // 50_000 B * 8 bits / 1000 / 2 s
        assertEquals(200, current.withRatesFrom(previous).inboundAudio?.kbps)
    }

    @Test
    fun `Given every stream When deriving rates Then each gets its own bitrate`() {
        val previous = RtcStatsSnapshot(
            timestamp = t0,
            inboundVideo = RtcStatsSnapshot.RtpStreamStats(bytes = 0),
            inboundAudio = RtcStatsSnapshot.RtpStreamStats(bytes = 0),
            outboundAudio = RtcStatsSnapshot.RtpStreamStats(bytes = 0),
        )
        val current = RtcStatsSnapshot(
            timestamp = t1,
            inboundVideo = RtcStatsSnapshot.RtpStreamStats(bytes = 125_000),
            inboundAudio = RtcStatsSnapshot.RtpStreamStats(bytes = 4_000),
            outboundAudio = RtcStatsSnapshot.RtpStreamStats(bytes = 2_000),
        )

        val derived = current.withRatesFrom(previous)

        assertEquals(1000, derived.inboundVideo?.kbps)
        assertEquals(32, derived.inboundAudio?.kbps)
        assertEquals(16, derived.outboundAudio?.kbps)
    }

    @Test
    fun `Given non-increasing bytes When deriving rates Then kbps stays zero`() {
        val previous = RtcStatsSnapshot(timestamp = t0, inboundVideo = RtcStatsSnapshot.RtpStreamStats(bytes = 25_000))
        val current = RtcStatsSnapshot(timestamp = t1, inboundVideo = RtcStatsSnapshot.RtpStreamStats(bytes = 25_000))

        assertEquals(0, current.withRatesFrom(previous).inboundVideo?.kbps)
    }
}
