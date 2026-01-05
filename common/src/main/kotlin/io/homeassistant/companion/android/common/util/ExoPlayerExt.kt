package io.homeassistant.companion.android.common.util

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cronet.CronetDataSource
import androidx.media3.datasource.cronet.CronetUtil
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import java.util.concurrent.Executors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.chromium.net.CronetEngine
import timber.log.Timber

/**
 * Initializes and returns an ExoPlayer instance optimized for live streaming,
 * utilizing Cronet with QUIC support for enhanced network performance and reduced latency.
 *
 * This function is called off the main thread ([Dispatchers.Default]) because [CronetEngine] initialization
 * triggers a GMS Dynamite module load, which is a slow operation that would block the UI.
 * Dynamite is used by the `full` flavor to load Cronet from Google Play Services at runtime.
 * This does not impact the `minimal` flavor which has Cronet embedded directly.
 *
 * Key features of this initialization:
 * - **Network Stack:** Uses Cronet as the underlying HTTP stack, enabling QUIC protocol support.
 *   Falls back to [DefaultHttpDataSource] if Cronet is unavailable on the device.
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
suspend fun initializePlayer(context: Context): ExoPlayer = withContext(Dispatchers.Default) {
    val dataSourceFactory = createDataSourceFactory(context)
    return@withContext ExoPlayer.Builder(context).setMediaSourceFactory(
        DefaultMediaSourceFactory(dataSourceFactory).setLiveMaxSpeed(8.0f),
    ).setLoadControl(
        DefaultLoadControl.Builder().setBufferDurationsMs(
            0,
            30000,
            0,
            0,
        ).build(),
    ).build()
}

/**
 * Creates a [DataSource.Factory] for ExoPlayer, preferring Cronet with QUIC support.
 * Falls back to [DefaultHttpDataSource] if Cronet providers are unavailable on the device.
 */
@OptIn(UnstableApi::class)
private fun createDataSourceFactory(context: Context): DataSource.Factory {
    val cronetEngine = CronetUtil.buildCronetEngine(context, null, true)

    return if (cronetEngine == null) {
        Timber.w("Failed to build cronet engine fallback to DefaultHttpDataSource")
        DefaultHttpDataSource.Factory()
    } else {
        CronetDataSource.Factory(cronetEngine, Executors.newSingleThreadExecutor())
    }
}
