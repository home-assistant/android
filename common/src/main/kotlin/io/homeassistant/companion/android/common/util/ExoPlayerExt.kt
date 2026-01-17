package io.homeassistant.companion.android.common.util

import android.content.Context
import android.net.http.HttpEngine
import android.os.Build
import android.os.ext.SdkExtensions
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpEngineDataSource
import androidx.media3.datasource.cronet.CronetDataSource
import androidx.media3.datasource.cronet.CronetUtil
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import dagger.Lazy
import java.io.File
import java.util.concurrent.Executors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
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
suspend fun initializePlayer(context: Context, dataSourceFactory: DataSource.Factory): ExoPlayer = withContext(Dispatchers.Default) {
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
internal fun createDataSourceFactory(
    context: Context,
    okHttpClientProvider: Lazy<OkHttpClient?>,
    cacheDirectory: File,
): DataSource.Factory {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && SdkExtensions.getExtensionVersion(Build.VERSION_CODES.S) >= 7) {
        // Use Platform embedded Cronet via android.net.http.HttpEngine
        val httpEngine = HttpEngine.Builder(context)
            .apply {
                // Cache QUIC data for faster initial connections
                val storageDirectory = cacheDirectory.resolve("httpEngineStorage")
                storageDirectory.mkdirs()
                if (storageDirectory.exists()) {
                    setStoragePath(storageDirectory.path)
                }
            }
            .build()
        Timber.i("Using HttpEngineDataSource for media")
        HttpEngineDataSource.Factory(httpEngine, Executors.newSingleThreadExecutor())
    } else {
        val cronetEngine = CronetUtil.buildCronetEngine(context, null, true)

        if (cronetEngine != null) {
            Timber.i("Using CronetDataSource for media")
            CronetDataSource.Factory(cronetEngine, Executors.newSingleThreadExecutor())
        } else {
            val okHttpClient = okHttpClientProvider.get()
            if (okHttpClient != null) {
                // Reuse OkHttpClient instance for Http/2 support
                Timber.w("Failed to build cronet engine fallback to OkHttpDataSource")
                OkHttpDataSource.Factory(okHttpClient)
            } else {
                Timber.w("Failed to build cronet engine fallback to DefaultDataSource")
                DefaultDataSource.Factory(context)
            }
        }
    }
}
