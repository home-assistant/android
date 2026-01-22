package io.homeassistant.companion.android.common.util

import android.content.Context
import android.net.http.HttpEngine
import android.os.Build
import android.os.ext.SdkExtensions
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
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
suspend fun initializePlayer(context: Context, dataSourceFactory: DataSource.Factory): ExoPlayer =
    withContext(Dispatchers.Default) {
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
 * Creates a [DataSource.Factory] for ExoPlayer, selecting the most optimal HTTP stack
 * available on the device to improve streaming performance.
 *
 * The selection logic is as follows:
 * 1.  **HttpEngine (Android 12+):** On devices running Android 12 with a sufficient extension
 *     level (SDK 31, extension 7+), it uses the platform's native `HttpEngine`. This provides
 *     Cronet capabilities, including QUIC and HTTP/3 support, without adding a dependency
 *     on Google Play Services. A cache directory is configured to store QUIC handshake data.
 * 2.  **Cronet (via GMS or embedded):** If `HttpEngine` is not available, it attempts to build
 *     a `CronetEngine`. This can come from Google Play Services (in the `full` flavor) or be
 *     embedded directly (in the `minimal` flavor). This also provides QUIC support.
 * 3.  **OkHttp:** If Cronet cannot be initialized, it falls back to using the app's shared
 *     `OkHttpClient` instance. This provides modern features like HTTP/2 but lacks QUIC.
 */
@OptIn(UnstableApi::class)
internal fun createDataSourceFactory(context: Context, okHttpClientProvider: Lazy<OkHttpClient>): DataSource.Factory {
    val httpEngineFactory = buildHttpEngineFactory(context)

    return if (httpEngineFactory != null) {
        httpEngineFactory
    } else {
        val cronetEngine = CronetUtil.buildCronetEngine(context, null, true)

        if (cronetEngine != null) {
            Timber.i("Using CronetDataSource for media")
            // assumed to be singleton scoped, so app lifetime for executor
            val singleThreadExecutor = Executors.newSingleThreadExecutor()
            CronetDataSource.Factory(cronetEngine, singleThreadExecutor)
        } else {
            // Reuse OkHttpClient instance for Http/2 support
            Timber.w("Failed to build cronet engine fallback to OkHttpDataSource")
            OkHttpDataSource.Factory(okHttpClientProvider.get())
        }
    }
}

/**
 * Builds and configures an `HttpEngineDataSource.Factory` for use with ExoPlayer on
 * devices supporting the platform's native `HttpEngine` (Android 12 with extension 7+).
 */
@OptIn(UnstableApi::class)
private fun buildHttpEngineFactory(context: Context): DataSource.Factory? {
    // https://developer.android.com/reference/android/net/http/HttpEngine
    // Added in API level 34 also in S Extensions 7
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
        SdkExtensions.getExtensionVersion(Build.VERSION_CODES.S) >= 7
    ) {
        // Use Platform embedded Cronet via android.net.http.HttpEngine
        val httpEngine = HttpEngine.Builder(context)
            .apply {
                // Cache QUIC data for faster initial connections
                val storageDirectory = File(context.cacheDir, "httpEngineStorage")
                if (!storageDirectory.exists()) {
                    val created = storageDirectory.mkdirs()
                    if (!created && !storageDirectory.exists()) {
                        Timber.w("Failed to create HttpEngine storage directory at path=${storageDirectory.path}")
                    }
                }
                if (storageDirectory.exists()) {
                    setStoragePath(storageDirectory.path)
                }
            }
            .build()
        Timber.i("Using HttpEngineDataSource for media")
        // assumed to be singleton scoped, so app lifetime for executor
        val singleThreadExecutor = Executors.newSingleThreadExecutor()
        HttpEngineDataSource.Factory(httpEngine, singleThreadExecutor)
    } else {
        null
    }
}

/**
 * A [DataSource.Factory] that defers the creation of its underlying (delegate) factory
 * until a [DataSource] is actually requested.
 *
 * This is useful for expensive factory creation processes, such as those involving I/O
 * or complex initialization (like building a `CronetEngine`). By wrapping the creation
 * logic in a lambda and passing it to this class, the expensive work is postponed
 * from the initial setup phase to the moment playback is about to begin.
 */
class DeferredCreationDataSource(private val factory: () -> DataSource.Factory) : DataSource.Factory {
    val delegate by lazy { factory() }

    @OptIn(UnstableApi::class)
    override fun createDataSource(): DataSource {
        return delegate.createDataSource()
    }
}
