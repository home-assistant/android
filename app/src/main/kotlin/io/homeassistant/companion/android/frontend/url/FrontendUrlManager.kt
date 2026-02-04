package io.homeassistant.companion.android.frontend.url

import dagger.hilt.android.scopes.ViewModelScoped
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.data.servers.UrlState
import io.homeassistant.companion.android.frontend.session.ServerSessionManager
import io.homeassistant.companion.android.frontend.session.SessionCheckResult
import io.homeassistant.companion.android.util.UrlUtil
import java.net.URL
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import timber.log.Timber

/**
 * Manages URL resolution and security checks for the frontend.
 *
 * This class handles:
 * - Server URL resolution with path handling
 * - Session authentication verification
 * - Insecure connection detection
 * - Security level configuration tracking
 * - Adding `external_auth=1` query parameter to signal the Home Assistant frontend
 *   that authentication will be provided via the JavaScript bridge
 */
@ViewModelScoped
class FrontendUrlManager @Inject constructor(
    private val serverManager: ServerManager,
    private val sessionManager: ServerSessionManager,
) {

    /**
     * Tracks whether the ConnectionSecurityLevel has been shown for each server
     * during this viewModel lifecycle. Once shown for a specific server, the screen
     * won't be shown again for that server.
     *
     * Key: server ID, Value: `true` if already shown
     */
    private val connectionSecurityLevelShown = hashMapOf<Int, Boolean>()

    /**
     * Retrieve URL for server. Returns a Flow that emits URL updates when connection state changes.
     *
     * The path parameter is only applied to the first emission to handle deep links.
     * Subsequent emissions (e.g., when switching between internal/external URLs) use only the base URL.
     *
     * @param serverId The server ID to use (can be [ServerManager.SERVER_ID_ACTIVE])
     * @param path Optional path to append to the initial URL (e.g., deep link path)
     * @return Flow of [UrlLoadResult] that emits when URL state changes
     */
    fun serverUrlFlow(serverId: Int, path: String?): Flow<UrlLoadResult> = flow {
        val server = serverManager.getServer(serverId)
        if (server == null) {
            Timber.e("Server not found for id: $serverId")
            emit(UrlLoadResult.ServerNotFound(serverId))
            return@flow
        }

        val actualServerId = server.id
        if (sessionManager.isSessionConnected(actualServerId) is SessionCheckResult.NotConnected) {
            Timber.w("Session not connected for server: $actualServerId")
            emit(UrlLoadResult.SessionNotConnected(actualServerId))
            return@flow
        }

        serverManager.activateServer(actualServerId)

        var pathConsumed = false
        serverManager.connectionStateProvider(actualServerId).urlFlow().collect { urlState ->
            val currentPath = if (pathConsumed) null else path
            pathConsumed = true

            val result = handleUrlState(
                serverId = actualServerId,
                urlState = urlState,
                path = currentPath,
            )
            emit(result)
        }
    }

    private suspend fun handleUrlState(serverId: Int, urlState: UrlState, path: String?): UrlLoadResult {
        return when (urlState) {
            is UrlState.HasUrl -> {
                buildUrl(
                    baseUrl = urlState.url,
                    serverId = serverId,
                    path = path,
                )
            }

            UrlState.InsecureState -> {
                Timber.w("Insecure connection blocked for server: $serverId")
                val securityState = serverManager.connectionStateProvider(serverId).getSecurityState()
                UrlLoadResult.InsecureBlocked(
                    serverId = serverId,
                    missingHomeSetup = !securityState.hasHomeSetup,
                    missingLocation = !securityState.locationEnabled,
                )
            }
        }
    }

    private suspend fun buildUrl(baseUrl: URL?, serverId: Int, path: String?): UrlLoadResult {
        // Build URL with path (skip path handling if it starts with "entityId:")
        val urlToLoad = if (path != null && !path.startsWith("entityId:")) {
            UrlUtil.handle(baseUrl, path)
        } else {
            baseUrl
        }

        if (urlToLoad == null) {
            Timber.e("No URL available for server: $serverId")
            return UrlLoadResult.NoUrlAvailable(serverId)
        }

        // Check if security level needs to be configured before loading
        val shouldShowSecurityLevel = shouldSetSecurityLevel(serverId) &&
            !connectionSecurityLevelShown.getOrPut(serverId) { false }

        if (shouldShowSecurityLevel) {
            Timber.d("Security level not set for server $serverId, showing SecurityLevelRequired")
            return UrlLoadResult.SecurityLevelRequired(serverId)
        }

        // Add external_auth=1 query parameter for authentication
        val httpUrl = urlToLoad.toString().toHttpUrlOrNull()
        if (httpUrl == null) {
            Timber.e("Failed to parse URL: $urlToLoad")
            return UrlLoadResult.NoUrlAvailable(serverId)
        }

        val urlWithAuth = httpUrl.newBuilder()
            .addQueryParameter("external_auth", "1")
            .build()
            .toString()

        Timber.d("Loading server URL: $urlWithAuth")
        return UrlLoadResult.Success(url = urlWithAuth, serverId = serverId)
    }

    private suspend fun shouldSetSecurityLevel(serverId: Int): Boolean {
        val connection = serverManager.getServer(serverId)?.connection ?: return false
        if (!connection.hasPlainTextUrl) {
            return false
        }
        return connection.allowInsecureConnection == null
    }

    /**
     * Mark security level as configured for server.
     *
     * After calling this, [serverUrlFlow] will no longer return [UrlLoadResult.SecurityLevelRequired]
     * for this server during the current session.
     *
     * @param serverId The server ID that had security level configured
     */
    fun onSecurityLevelConfigured(serverId: Int) {
        connectionSecurityLevelShown[serverId] = true
    }
}
