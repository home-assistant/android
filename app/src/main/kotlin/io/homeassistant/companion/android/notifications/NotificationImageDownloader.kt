package io.homeassistant.companion.android.notifications

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.annotation.VisibleForTesting
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.data.servers.UrlState
import io.homeassistant.companion.android.common.util.di.SuspendProvider
import io.homeassistant.companion.android.util.UrlUtil
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber

/**
 * Downloads images referenced by notification payloads, authenticating against the server when
 * the image is served by Home Assistant itself.
 */
@Singleton
class NotificationImageDownloader @VisibleForTesting constructor(
    private val okHttpClientProvider: SuspendProvider<OkHttpClient>,
    private val serverManager: ServerManager,
    private val backgroundDispatcher: CoroutineDispatcher,
) {

    @Inject
    constructor(okHttpClientProvider: SuspendProvider<OkHttpClient>, serverManager: ServerManager) :
        this(okHttpClientProvider, serverManager, Dispatchers.IO)

    /**
     * Download and decode the image at [url].
     *
     * @param requiresAuth `true` to send the server bearer token, which is needed for server relative paths
     * @return the decoded bitmap or `null` if [url] is `null` or the download failed
     */
    suspend fun download(serverId: Int, url: URL?, requiresAuth: Boolean = false): Bitmap? = withContext(
        backgroundDispatcher,
    ) {
        if (url == null) {
            return@withContext null
        }

        var image: Bitmap? = null
        try {
            val request = Request.Builder().apply {
                url(url)
                if (requiresAuth) {
                    addHeader("Authorization", serverManager.authenticationRepository(serverId).buildBearerToken())
                }
            }.build()

            okHttpClientProvider().newCall(request).execute().use {
                image = BitmapFactory.decodeStream(it.body.byteStream())
            }
        } catch (e: Exception) {
            Timber.e(e, "Couldn't download image for notification")
        }
        return@withContext image
    }

    /**
     * Download an image given as a server relative path or an absolute URL, resolved against the
     * URL of the server [serverId].
     *
     * @return the decoded bitmap or `null` if the server URL is unavailable or the download failed
     */
    suspend fun downloadServerImage(serverId: Int, path: String): Bitmap? {
        val imagePath = path.trim().replace(" ", "%20")
        val urlState = serverManager.connectionStateProvider(serverId).urlFlow().first()
        if (urlState !is UrlState.HasUrl) {
            Timber.w("Not fetching image since URL is unavailable")
            return null
        }
        return download(serverId, UrlUtil.handle(urlState.url, imagePath), !UrlUtil.isAbsoluteUrl(imagePath))
    }
}
