package io.homeassistant.companion.android.util

import android.graphics.Bitmap
import androidx.annotation.VisibleForTesting
import io.homeassistant.companion.android.common.data.integration.isPersonOf
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.data.servers.firstUrlOrNull
import io.homeassistant.companion.android.imageloader.HAImageCachePolicy
import io.homeassistant.companion.android.imageloader.HAImageLoader
import io.homeassistant.companion.android.imageloader.HAImageRequest
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import timber.log.Timber

private const val ATTRIBUTE_ENTITY_PICTURE = "entity_picture"
private const val HEADER_AUTHORIZATION = "Authorization"

/**
 * Cache key for a user avatar.
 *
 * Derived from the stable [serverId] and the server-relative [picturePath] (the `entity_picture`
 * value) rather than the absolute URL: the base URL resolved at download time can be the internal
 * or external address depending on the current network, and keying on it would store the same image
 * twice and re-download it whenever the network changes.
 */
@VisibleForTesting
internal fun avatarCacheKey(serverId: Int, picturePath: String): String = "server-$serverId-$picturePath"

/**
 * Loads the profile picture of a server's current user.
 *
 * The picture is taken from the `person` entity linked to the user and authenticated with the
 * server's bearer token. Results are cached by [HAImageLoader], so repeated calls do not re-download.
 */
class ServerUserAvatarUseCase @Inject constructor(
    private val imageLoader: HAImageLoader,
    private val serverManager: ServerManager,
) {
    /**
     * Returns the current user's profile picture for the server identified by [serverId], or `null`
     * when there is no resolvable picture for it.
     *
     * The picture is only downloaded when there is a base URL and credentials can be sent to it
     * safely (the picture is authenticated, so an unsafe request would just fail). Otherwise — no
     * base URL (server unreachable) or credentials unsafe — a previously cached copy is returned if
     * one exists.
     */
    suspend fun getUserAvatar(serverId: Int): Bitmap? {
        val userId = serverManager.getServer(serverId)?.user?.id ?: return null
        return try {
            val picturePath = findPersonPicturePath(serverId = serverId, userId = userId) ?: return null
            val cacheKey = avatarCacheKey(serverId = serverId, picturePath = picturePath)
            val url = picturePath.toAbsoluteUrl(serverId = serverId)
            if (url != null && serverManager.connectionStateProvider(serverId).canSafelySendCredentials(url)) {
                downloadAvatar(serverId = serverId, url = url, cacheKey = cacheKey)
            } else {
                imageLoader.getCachedBitmap(cacheKey)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Could not load user avatar for serverId=$serverId")
            null
        }
    }

    private suspend fun findPersonPicturePath(serverId: Int, userId: String): String? {
        val entities = serverManager.integrationRepository(serverId).getEntities().orEmpty()
        return entities
            .firstOrNull { it.isPersonOf(userId) }
            ?.attributes?.get(ATTRIBUTE_ENTITY_PICTURE) as? String
    }

    private suspend fun String.toAbsoluteUrl(serverId: Int): String? {
        if (startsWith("http")) return this
        val baseUrl = serverManager.connectionStateProvider(serverId)
            .urlFlow()
            .firstUrlOrNull()
            ?.toString()
            ?.removeSuffix("/")
            ?: return null
        return "$baseUrl$this"
    }

    private suspend fun downloadAvatar(serverId: Int, url: String, cacheKey: String): Bitmap? {
        val token = serverManager.authenticationRepository(serverId).buildBearerToken()
        return imageLoader.loadBitmap(
            HAImageRequest(
                url = url,
                cachePolicy = HAImageCachePolicy.Keyed(key = cacheKey),
                headers = mapOf(HEADER_AUTHORIZATION to token),
            ),
        )
    }
}
