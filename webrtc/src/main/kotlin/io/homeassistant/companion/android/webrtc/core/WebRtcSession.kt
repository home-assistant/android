package io.homeassistant.companion.android.webrtc.core

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** Default cadence at which [observeStats] reads the peer-connection statistics. */
private val DEFAULT_STATS_POLL_INTERVAL: Duration = 1.seconds

/**
 * A WebRTC session: one peer connection carrying the media legs requested in [MediaOptions].
 *
 * A session is a connection with a composable set of media capabilities, each exposed as a handle
 * that is non-null only when its direction was negotiated:
 * [videoIn] for incoming video, [audioIn] for incoming audio, [audioOut] for the outgoing
 * microphone. A talk-only session has only [audioOut]; a video session has [videoIn] (and maybe
 * the audio handles).
 *
 * Obtained from [WebRtcSessionFactory]; the backing engine is never constructed by consumers. The
 * lifecycle functions ([start], [stop], [release]) suspend and serialize with each other, so they
 * are safe to call concurrently from any coroutine.
 */
interface WebRtcSession {
    /** Current connection state. */
    val state: StateFlow<ConnectionState>

    /** Incoming video, or null when video-in was not negotiated. */
    val videoIn: VideoReceiver?

    /** Incoming audio, or null when audio-in was not negotiated. */
    val audioIn: AudioReceiver?

    /** Outgoing microphone, or null when audio-out was not negotiated. */
    val audioOut: AudioSender?

    /**
     * Emits a [RtcStatsSnapshot] every [interval] while collected, with per-stream bitrates
     * derived between consecutive snapshots. For diagnostics.
     */
    fun observeStats(interval: Duration = DEFAULT_STATS_POLL_INTERVAL): Flow<RtcStatsSnapshot>

    /** Start the session. Does nothing if already started, [release]d, or no stream was requested. */
    suspend fun start()

    /** Stop the session and end it on the server. It can be started again with [start]. */
    suspend fun stop()

    /** Stop and release all resources. The session cannot be used afterwards. */
    suspend fun release()
}
