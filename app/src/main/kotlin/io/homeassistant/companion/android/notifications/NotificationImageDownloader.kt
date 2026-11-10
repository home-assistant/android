package io.homeassistant.companion.android.notifications

import android.content.Context
import android.graphics.Bitmap
import coil3.imageLoader
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.CachePolicy
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.size.Size
import coil3.toBitmap
import dagger.hilt.android.qualifiers.ApplicationContext
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.data.servers.UrlState
import io.homeassistant.companion.android.util.UrlUtil
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.first
import timber.log.Timber

private const val HEADER_AUTHORIZATION = "Authorization"

/**
 * Downloads images referenced by a server path or URL, authenticating against the server when the
 * image is served by Home Assistant itself.
 */
@Singleton
class NotificationImageDownloader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val serverManager: ServerManager,
) {

    /**
     * Download an image given as a server relative path or an absolute URL, resolved against the
     * URL of the server [serverId].
     *
     * @param size bounds the decoded bitmap
     * @return the decoded bitmap or `null` if the server URL is unavailable or the download failed
     */
    suspend fun downloadServerImage(serverId: Int, path: String, size: Size): Bitmap? {
        val imagePath = path.trim().replace(" ", "%20")
        val urlState = serverManager.connectionStateProvider(serverId).urlFlow().first()
        val url = (urlState as? UrlState.HasUrl)?.url?.let { UrlUtil.handle(it, imagePath) }
        if (url == null) {
            Timber.w("Not fetching image since URL is unavailable")
            return null
        }
        return try {
            val request = ImageRequest.Builder(context)
                .data(url.toString())
                .apply {
                    if (!UrlUtil.isAbsoluteUrl(imagePath)) {
                        val token = serverManager.authenticationRepository(serverId).buildBearerToken()
                        httpHeaders(NetworkHeaders.Builder().add(HEADER_AUTHORIZATION, token).build())
                    }
                }
                .size(size)
                .allowHardware(false)
                .memoryCachePolicy(CachePolicy.DISABLED)
                .diskCachePolicy(CachePolicy.DISABLED)
                .build()
            when (val result = context.imageLoader.execute(request)) {
                is SuccessResult -> result.image.toBitmap()

                is ErrorResult -> {
                    Timber.e(result.throwable, "Couldn't download image for serverId=$serverId")
                    null
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Couldn't download image for serverId=$serverId")
            null
        }
    }
}
