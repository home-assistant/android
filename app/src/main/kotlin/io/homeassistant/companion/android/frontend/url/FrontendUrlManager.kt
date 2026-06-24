package io.homeassistant.companion.android.frontend.url

import dagger.hilt.android.scopes.ViewModelScoped
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.data.servers.UrlState
import io.homeassistant.companion.android.frontend.navigation.FrontendTarget
import io.homeassistant.companion.android.frontend.session.ServerSessionManager
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
     * The target is only applied to the first emission to handle deep links.
     * Subsequent emissions (e.g., when switching between internal/external URLs) load only the base URL.
     *
     * @param serverId The server ID to use (can be [ServerManager.SERVER_ID_ACTIVE])
     * @param target The frontend destination to open on the initial URL (e.g., a deep link)
     * @return Flow of [UrlLoadResult] that emits when URL state changes
     */
    fun serverUrlFlow(serverId: Int, target: FrontendTarget): Flow<UrlLoadResult> = flow {
        val server = serverManager.getServer(serverId)
        if (server == null) {
            Timber.e("Server not found for id: $serverId")
            emit(UrlLoadResult.ServerNotFound(serverId))
            return@flow
        }

        val actualServerId = server.id
        if (!sessionManager.isSessionConnected(actualServerId)) {
            Timber.w("Session not connected for server: $actualServerId")
            emit(UrlLoadResult.SessionNotConnected(actualServerId))
            return@flow
        }

        serverManager.activateServer(actualServerId)

        var targetConsumed = false
        serverManager.connectionStateProvider(actualServerId).urlFlow().collect { urlState ->
            val currentTarget = if (targetConsumed) FrontendTarget.Default else target

            val result = handleUrlState(
                serverId = actualServerId,
                urlState = urlState,
                target = currentTarget,
            )
            // Only consume the target when a URL was actually loaded with it
            if (urlState is UrlState.HasUrl) {
                targetConsumed = true
            }
            emit(result)
        }
    }

    private suspend fun handleUrlState(serverId: Int, urlState: UrlState, target: FrontendTarget): UrlLoadResult {
        return when (urlState) {
            is UrlState.HasUrl -> {
                buildUrl(
                    baseUrl = urlState.url,
                    serverId = serverId,
                    target = target,
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

    private suspend fun buildUrl(baseUrl: URL?, serverId: Int, target: FrontendTarget): UrlLoadResult {
        // Set when the more-info dialog must be opened via the `more-info-entity-id` URL parameter
        // (HA 2025.6+) — added through the query builder below so the entity id is percent-encoded.
        var moreInfoEntityIdForQuery: String? = null
        // Set when the more-info dialog must instead be opened via JavaScript after the page loads
        // (older servers that don't honor the `more-info-entity-id` URL parameter).
        var moreInfoEntityIdForJs: String? = null
        val urlToLoad = when (target) {
            FrontendTarget.Default -> baseUrl
            is FrontendTarget.Path -> UrlUtil.handle(baseUrl, target.path)
            is FrontendTarget.EntityMoreInfo -> {
                if (supportsMoreInfoQueryParam(serverId)) {
                    moreInfoEntityIdForQuery = target.entityId
                } else {
                    moreInfoEntityIdForJs = target.entityId
                }
                baseUrl
            }
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
            .apply { moreInfoEntityIdForQuery?.let { addQueryParameter("more-info-entity-id", it) } }
            .addQueryParameter("external_auth", "1")
            .build()
            .toString()

        Timber.d("Loading server URL: $urlWithAuth")
        return UrlLoadResult.Success(url = urlWithAuth, serverId = serverId, moreInfoEntityId = moreInfoEntityIdForJs)
    }

    /**
     * Whether the server honors the `more-info-entity-id` URL query parameter (HA 2025.6+). Older
     * servers ignore it, so the more-info dialog must be opened via JavaScript after the page loads.
     */
    private suspend fun supportsMoreInfoQueryParam(serverId: Int): Boolean {
        return serverManager.getServer(serverId)?.version?.isAtLeast(2025, 6, 0) == true
    }

    private suspend fun shouldSetSecurityLevel(serverId: Int): Boolean {
        val connection = serverManager.getServer(serverId)?.connection ?: return false
        if (!connection.hasPlainTextUrl) {
            return false
        }
        return connection.allowInsecureConnection == null
    }

    /**
     * Mark security level as shown for a given server.
     *
     * After calling this, [serverUrlFlow] will no longer return [UrlLoadResult.SecurityLevelRequired]
     * for this server during the current session.
     *
     * @param serverId The server ID that had security level shown
     */
    fun onSecurityLevelShown(serverId: Int) {
        connectionSecurityLevelShown[serverId] = true
    }
}
