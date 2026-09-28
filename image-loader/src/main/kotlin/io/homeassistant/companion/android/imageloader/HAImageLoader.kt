package io.homeassistant.companion.android.imageloader

import android.graphics.Bitmap
import okhttp3.OkHttpClient

/**
 * App-wide image loader.
 *
 * Every image goes through the [OkHttpClient] given to [init], so requests get the app's TLS
 * configuration (for instance mTLS client certificates) and interceptors. Until [init] is called,
 * [loadBitmap] and [getCachedBitmap] suspend instead of falling back to a loader built without
 * that client.
 *
 * All returned bitmaps are software bitmaps, so they can be used anywhere, including in
 * `RemoteViews` which cannot serialize hardware bitmaps.
 */
interface HAImageLoader {

    /**
     * Makes the loader ready to serve requests using [okHttpClient] for every network call, and
     * resumes the requests waiting for it.
     *
     * Only the first call has an effect, later calls are ignored.
     */
    fun init(okHttpClient: OkHttpClient)

    /**
     * Loads the image described by [request], suspending until [init] has been called.
     *
     * Returns `null` if the image cannot be loaded (network error, HTTP error, undecodable data).
     */
    suspend fun loadBitmap(request: HAImageRequest): Bitmap?

    /**
     * Returns the image previously stored under [cacheKey] (see [HAImageCachePolicy.Enabled.key])
     * without touching the network, looking first in memory and then on disk. Suspends until
     * [init] has been called.
     *
     * Returns `null` on a cache miss.
     */
    suspend fun getCachedBitmap(cacheKey: String): Bitmap?
}

/**
 * Describes an image to load with [HAImageLoader.loadBitmap].
 *
 * @property url the URL of the image
 * @property size the size the image should be decoded at
 * @property cachePolicy whether the image is read from and written to the memory and disk caches
 * @property headers extra HTTP headers sent with the request, for instance `Authorization`
 */
data class HAImageRequest(
    val url: String,
    val size: HAImageSize = HAImageSize.Original,
    val cachePolicy: HAImageCachePolicy = HAImageCachePolicy.Enabled(),
    val headers: Map<String, String> = emptyMap(),
)

/** Size an image is decoded at, in pixels. */
sealed interface HAImageSize {

    /** Keep the original dimensions of the image. */
    data object Original : HAImageSize

    /**
     * Scale the image so it fits within [width] x [height] while keeping its aspect ratio, scaling it
     * up if it is smaller.
     */
    data class Exact(val width: Int, val height: Int) : HAImageSize {
        /** Fits the image within a [size] x [size] square. */
        constructor(size: Int) : this(width = size, height = size)
    }

    /**
     * Allow the image to not match [width] x [height] exactly: it is scaled down to roughly fit
     * within it, but never scaled up. Uses less memory than [Exact] for small images.
     */
    data class Inexact(val width: Int, val height: Int) : HAImageSize
}

/** Caching behavior of a [HAImageRequest]. */
sealed interface HAImageCachePolicy {

    /**
     * Read from and write to the memory and disk caches.
     *
     * @property key the key the image is stored under, to retrieve it later with
     * [HAImageLoader.getCachedBitmap]. When `null` the key is derived from the URL.
     */
    data class Enabled(val key: String? = null) : HAImageCachePolicy

    /** Always fetch the image, never read from nor write to the memory and disk caches. */
    data object Disabled : HAImageCachePolicy
}
