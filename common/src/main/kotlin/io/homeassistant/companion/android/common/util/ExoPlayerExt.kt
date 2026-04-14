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
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.chromium.net.CronetEngine
import timber.log.Timber

/**
 * Connection and read timeout for Cronet and HttpEngine data sources.
 *
 * Increased from the default 8 seconds to accommodate streams that may remain idle
 * before sending data, such as Assist TTS where the stream stays open until the user speaks.
 * With the default timeout, the connection would be closed even though the stream is still alive.
 */
private val DATA_SOURCE_TIMEOUT = 30.seconds

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
 * 3.  **OkHttp:** Falls back to the factory provided by [okHttpClientFactoryProvider]. The
 *     provider is only called when neither HttpEngine nor Cronet is available, avoiding
 *     unnecessary initialization of the OkHttp factory.
 *
 * @param okHttpClientFactoryProvider lazily provides an OkHttp-backed factory, only invoked
 *   when HttpEngine and Cronet are both unavailable
 */
@OptIn(UnstableApi::class)
private fun createDataSourceFactory(
    context: Context,
    okHttpClientFactoryProvider: () -> OkHttpDataSource.Factory,
): DataSource.Factory {
    val httpEngineFactory = buildHttpEngineFactory(context)

    return if (httpEngineFactory != null) {
        httpEngineFactory
    } else {
        val cronetEngine = CronetUtil.buildCronetEngine(context, null, true)

        if (cronetEngine != null) {
            Timber.i("Using CronetDataSource for media")
            // assumed to be singleton scoped, so app lifetime for executor
            val singleThreadExecutor = Executors.newSingleThreadExecutor()
            val timeout = DATA_SOURCE_TIMEOUT.inWholeMilliseconds.toInt()
            CronetDataSource.Factory(cronetEngine, singleThreadExecutor)
                .setConnectionTimeoutMs(timeout)
                .setReadTimeoutMs(timeout)
        } else {
            Timber.w("Failed to build cronet engine, falling back to OkHttpDataSource")
            okHttpClientFactoryProvider()
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
        val timeout = DATA_SOURCE_TIMEOUT.inWholeMilliseconds.toInt()
        HttpEngineDataSource.Factory(httpEngine, singleThreadExecutor)
            .setConnectionTimeoutMs(timeout)
            .setReadTimeoutMs(timeout)
    } else {
        null
    }
}

/**
 * A [DataSource.Factory] that dynamically selects between the best available HTTP stack
 * and OkHttp based on whether mTLS is currently active.
 *
 * HttpEngine and Cronet do not support client certificates, so when mTLS is configured
 * this factory falls back to OkHttp which has the [javax.net.ssl.SSLSocketFactory] configured
 * with the client certificate.
 *
 * The mTLS check is performed on every [createDataSource] call so that changes to the
 * certificate configuration (adding or removing mTLS) are picked up without an app restart.
 * The check is not per server, if one server in the current setup of the app is using mTLS
 * then we use the OkHttp datasource.
 *
 * @param context application context for initializing HttpEngine/Cronet
 * @param okHttpClientProvider lazily provides the shared [OkHttpClient] configured with mTLS
 * @param usesMtls called on every [createDataSource] to check if mTLS is currently used
 */
internal class MtlsAwareDataSourceFactory(
    context: Context,
    okHttpClientProvider: Lazy<OkHttpClient>,
    private val usesMtls: () -> Boolean,
) : DataSource.Factory {
    private val okHttpDelegate by lazy { OkHttpDataSource.Factory(okHttpClientProvider.get()) }
    private val defaultDelegate by lazy { createDataSourceFactory(context) { okHttpDelegate } }

    @OptIn(UnstableApi::class)
    override fun createDataSource(): DataSource {
        return if (usesMtls()) {
            okHttpDelegate
        } else {
            defaultDelegate
        }.createDataSource()
    }
}
