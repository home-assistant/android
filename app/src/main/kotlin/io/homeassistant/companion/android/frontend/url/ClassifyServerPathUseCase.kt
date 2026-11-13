package io.homeassistant.companion.android.frontend.url

import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.data.servers.webSocketRepositoryOrNull
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import okhttp3.HttpUrl
import timber.log.Timber

/**
 * First path segments under which Home Assistant serves content that is not the frontend
 * (API responses, files of the `www` folder, media, files of custom integrations, static assets).
 */
private val NON_FRONTEND_PATH_SEGMENTS = setOf("api", "local", "media", "hacsfiles", "static")

/** What Home Assistant serves under a path of the server. */
sealed interface ServerPathType {
    /** The path is served by the frontend. */
    data object Frontend : ServerPathType

    /** The path is served by something else than the frontend (API, file, ...). */
    data object NotFrontend : ServerPathType

    /**
     * The path could not be classified. It can be a frontend panel hidden from the user, for
     * which the frontend shows the default panel, or content that is not the frontend.
     */
    data object Unknown : ServerPathType
}

/**
 * Classifies a path of the server the same way Home Assistant does: the frontend is served on the
 * root and on the paths starting with the url path of a panel, anything else is not the frontend.
 */
class ClassifyServerPathUseCase @Inject constructor(private val serverManager: ServerManager) {

    /**
     * Classifies the path of [url], which must be on the origin of the server [serverId].
     *
     * The panels returned to a user who is not an admin leave out the panels restricted to
     * admins, so a path outside of the panels is only [ServerPathType.NotFrontend] for an admin
     * or when it starts with a segment known to not be the frontend.
     */
    suspend operator fun invoke(serverId: Int, url: HttpUrl): ServerPathType {
        val firstSegment = url.pathSegments.first()
        if (firstSegment.isEmpty()) return ServerPathType.Frontend

        val panelUrlPaths = getPanelUrlPaths(serverId)
        return when {
            panelUrlPaths != null && firstSegment in panelUrlPaths -> ServerPathType.Frontend
            firstSegment in NON_FRONTEND_PATH_SEGMENTS -> ServerPathType.NotFrontend
            panelUrlPaths != null && isAdmin(serverId) -> ServerPathType.NotFrontend
            else -> ServerPathType.Unknown
        }
    }

    private suspend fun getPanelUrlPaths(serverId: Int): Set<String>? {
        val panels = try {
            serverManager.webSocketRepositoryOrNull(serverId)?.getPanels()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Failed to get the panels of server $serverId")
            null
        }
        if (panels == null) Timber.w("Panels not available for server $serverId")
        return panels?.map { it.urlPath }?.toSet()
    }

    private suspend fun isAdmin(serverId: Int): Boolean = serverManager.getServer(serverId)?.user?.isAdmin == true
}
