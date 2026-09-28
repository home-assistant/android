package io.homeassistant.companion.android.imageloader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.annotation.VisibleForTesting
import coil3.ImageLoader
import coil3.memory.MemoryCache
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.size.Precision
import coil3.size.Size
import coil3.toBitmap
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import timber.log.Timber

/**
 * [HAImageLoader] backed by Coil.
 *
 * The Coil [ImageLoader] is only built once [init] provides the [OkHttpClient]; requests made before
 * that wait on [imageLoader]. Coil's own singleton (`SingletonImageLoader`) is never used, so no
 * request can end up on a loader missing the app's [OkHttpClient].
 */
@Singleton
internal class CoilHAImageLoader @VisibleForTesting constructor(
    private val context: Context,
    private val imageLoaderFactory: (OkHttpClient) -> ImageLoader,
) : HAImageLoader {

    @Inject
    constructor(@ApplicationContext context: Context) : this(
        context = context,
        imageLoaderFactory = { okHttpClient ->
            ImageLoader.Builder(context)
                .components { add(OkHttpNetworkFetcherFactory(callFactory = okHttpClient)) }
                .build()
        },
    )

    private val imageLoader = CompletableDeferred<ImageLoader>()

    override fun init(okHttpClient: OkHttpClient) {
        if (imageLoader.isCompleted) {
            Timber.w("HAImageLoader is already initialized, ignoring")
            return
        }
        val loader = imageLoaderFactory(okHttpClient)
        if (!imageLoader.complete(loader)) {
            // Lost a race against a concurrent init, keep the loader that won.
            loader.shutdown()
        }
    }

    override suspend fun loadBitmap(request: HAImageRequest): Bitmap? {
        val loader = imageLoader.await()
        return loader.execute(request.toCoilRequest()).image?.toBitmap()
    }

    override suspend fun getCachedBitmap(cacheKey: String): Bitmap? {
        val loader = imageLoader.await()
        loader.memoryCache?.get(MemoryCache.Key(cacheKey))?.image?.toBitmap()?.let { return it }

        val diskCache = loader.diskCache ?: return null
        return withContext(Dispatchers.IO) {
            diskCache.openSnapshot(cacheKey)?.use { snapshot ->
                BitmapFactory.decodeFile(snapshot.data.toString())
            }
        }
    }

    private fun HAImageRequest.toCoilRequest(): ImageRequest = ImageRequest.Builder(context)
        .data(url)
        // Return software bitmaps so they can be drawn anywhere, including in RemoteViews.
        .allowHardware(false)
        .applySize(size)
        .applyCachePolicy(cachePolicy)
        .apply {
            if (headers.isNotEmpty()) {
                httpHeaders(
                    NetworkHeaders.Builder()
                        .apply { headers.forEach { (name, value) -> add(name, value) } }
                        .build(),
                )
            }
        }
        .build()
}

private fun ImageRequest.Builder.applySize(size: HAImageSize): ImageRequest.Builder = when (size) {
    HAImageSize.Original -> this
    is HAImageSize.Exact -> size(Size(size.width, size.height)).precision(Precision.EXACT)
    is HAImageSize.Inexact -> size(Size(size.width, size.height)).precision(Precision.INEXACT)
}

private fun ImageRequest.Builder.applyCachePolicy(policy: HAImageCachePolicy): ImageRequest.Builder = when (policy) {
    is HAImageCachePolicy.Enabled -> apply {
        policy.key?.let { key ->
            memoryCacheKey(key)
            diskCacheKey(key)
        }
    }

    HAImageCachePolicy.Disabled -> diskCachePolicy(CachePolicy.DISABLED).memoryCachePolicy(CachePolicy.DISABLED)
}
