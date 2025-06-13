package io.homeassistant.companion.android.util.compose

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cronet.CronetDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import java.util.concurrent.Executors
import org.chromium.net.CronetEngine

/**
 * Initializes and returns an ExoPlayer instance optimized for live streaming,
 * utilizing Cronet with QUIC support for enhanced network performance and reduced latency.
 *
 * Key features of this initialization:
 * - **Network Stack:** Uses Cronet as the underlying HTTP stack, enabling QUIC protocol support.
 * - **Threading:** Network operations are handled on a dedicated background thread,
 *   created internally via [Executors.newSingleThreadExecutor], to prevent UI blocking.
 * - **Buffering:** Configured with a [DefaultLoadControl] specifically tuned to minimize
 *   initial buffering and rebuffering events for live content.
 * - **Playback Speed Control:** Sets the maximum playback speed for live streams to 8.0x,
 *   allowing users to catch up more quickly if they fall behind the live edge.
 *
 * @return A fully configured [ExoPlayer] instance ready for live stream playback.
 * @see DefaultLoadControl
 * @see Executors.newSingleThreadExecutor
 */
@OptIn(UnstableApi::class)
fun initializePlayer(context: Context): ExoPlayer {
    return ExoPlayer.Builder(context).setMediaSourceFactory(
        DefaultMediaSourceFactory(
            CronetDataSource.Factory(
                CronetEngine.Builder(context).enableQuic(true).build(),
                Executors.newSingleThreadExecutor(),
            ),
        ).setLiveMaxSpeed(8.0f),
    ).setLoadControl(
        DefaultLoadControl.Builder().setBufferDurationsMs(
            0,
            30000,
            0,
            0,
        ).build(),
    ).build()
}
