package io.homeassistant.companion.android.launch.link

import android.net.Uri
import androidx.core.net.toUri
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.util.FailFast
import io.homeassistant.companion.android.database.server.Server
import io.homeassistant.companion.android.frontend.navigation.FrontendTarget
import javax.inject.Inject
import timber.log.Timber

private const val REDIRECT_URL_PATH = "/redirect/"
private const val INVITE_URL_PATH = "/invite/"
private const val NAVIGATE_URL_PATH = "/navigate/"

private const val MY_BASE_DOMAIN = "my.home-assistant.io"
private const val BASE_MY_REDIRECT_URL = "https://$MY_BASE_DOMAIN$REDIRECT_URL_PATH"

internal const val HA_DEEP_LINK_SCHEME = "homeassistant"

private const val INTERNAL_MY_REDIRECT_PREFIX = "_my_redirect/"

private const val MOBILE_PARAM = "mobile"
private const val MOBILE_VALUE = "1"

private const val SERVER_PARAM = "server"
private const val SERVER_ID_PARAM = "server_id"
private const val MORE_INFO_ENTITY_ID_PARAM = "more-info-entity-id"

/**
 * Builds the `homeassistant://navigate` deep link that [LinkHandler] resolves back to [target] on
 * the server identified by [serverId]. Used for persisted intents (e.g. shortcuts) so they target
 * the stable, exported [LinkActivity] entry point rather than an internal activity.
 */
internal fun navigateDeepLinkUri(target: FrontendTarget, serverId: Int): Uri {
    val builder = Uri.Builder()
        .scheme(HA_DEEP_LINK_SCHEME)
        .authority(NAVIGATE_URL_PATH.removeSurrounding("/"))
    when (target) {
        is FrontendTarget.EntityMoreInfo -> builder.appendQueryParameter(MORE_INFO_ENTITY_ID_PARAM, target.entityId)
        is FrontendTarget.Path -> builder.path(target.path)
        FrontendTarget.Default -> Unit
    }
    return builder.appendQueryParameter(SERVER_ID_PARAM, serverId.toString()).build()
}

/**
 * Sealed class that represents the destination of a link.
 * Each destination contains the relevant data to open the destination based on the data present in the link.
 */
sealed interface LinkDestination {
    /**
     * Open a server onboarding flow with the given [serverUrl].
     */
    data class Onboarding(val serverUrl: String) : LinkDestination

    /**
     * Open the frontend at [target] on the server identified by [serverId].
     */
    data class Webview(val target: FrontendTarget, val serverId: Int) : LinkDestination

    /**
     * Let the user pick one of the registered [servers] before opening the frontend at [target].
     */
    data class ServerPicker(val target: FrontendTarget, val servers: List<Server>) : LinkDestination

    /**
     * Nowhere to go from this link.
     */
    data object NoDestination : LinkDestination
}

/**
 * Handles universal links from `https://my.home-assistant.io` and some of the deep links from `homeassistant://`
 */
interface LinkHandler {
    /**
     * Processes the given [uri] from `https://my.home-assistant.io` or `homeassistant://` and determines the
     * intended navigation destination within the application.
     *
     * @param uri The universal link to handle.
     * @return A [LinkDestination] indicating where the link should navigate to,
     *         or [LinkDestination.NoDestination] if the link is unhandled or invalid.
     */
    suspend fun handleLink(uri: Uri): LinkDestination
}

class LinkHandlerImpl @Inject constructor(private val serverManager: ServerManager) : LinkHandler {

    override suspend fun handleLink(uri: Uri): LinkDestination {
        return when (uri.scheme) {
            "https" -> handleUniversalLink(uri)
            HA_DEEP_LINK_SCHEME -> handleDeepLink(uri)
            else -> {
                FailFast.fail {
                    "Invalid link scheme: $uri"
                }
                LinkDestination.NoDestination
            }
        }
    }

    private suspend fun webviewDestination(target: FrontendTarget, serverId: Int? = null): LinkDestination {
        if (serverId != null) {
            return LinkDestination.Webview(target, serverId)
        }

        val servers = serverManager.servers()
        return if (servers.size <= 1) {
            LinkDestination.Webview(target, ServerManager.SERVER_ID_ACTIVE)
        } else {
            LinkDestination.ServerPicker(target, servers)
        }
    }

    private suspend fun handleUniversalLink(uri: Uri): LinkDestination {
        val path = uri.path.orEmpty()

        if (uri.host != MY_BASE_DOMAIN) {
            FailFast.fail {
                "Invalid deep link host: $uri should be $MY_BASE_DOMAIN"
            }
            return LinkDestination.NoDestination
        }
        return when {
            path.startsWith(REDIRECT_URL_PATH) -> handleRedirectLink(uri)
            path.startsWith(INVITE_URL_PATH) -> handleInviteLink(uri)
            else -> {
                FailFast.fail { "Unknown or invalid universal link: $uri" }
                LinkDestination.NoDestination
            }
        }
    }

    private suspend fun handleDeepLink(uri: Uri): LinkDestination {
        return when {
            uri.host == INVITE_URL_PATH.removeSurrounding("/") -> handleInviteLink(uri)
            uri.host == NAVIGATE_URL_PATH.removeSurrounding("/") -> handleNavigateLink(uri)
            else -> {
                FailFast.fail { "Unknown or invalid deep link scheme: $uri" }
                LinkDestination.NoDestination
            }
        }
    }

    /**
     * Attempts to extract the target Home Assistant instance URL from the fragment part of the provided URI.
     *
     * The expected invitation link format is:
     * Universal Link: `https://my.home-assistant.io/invite#url=http://homeassistant.local:8123`
     * Deep Link: `homeassistant://invite/#url=http://homeassistant.local:8123`
     *
     * The target URL is embedded in the fragment for security reasons,
     * preventing it from being sent to `my.home-assistant.io`. This function extracts the
     * `url` parameter from the fragment.
     *
     * @param uri The URI to process containing the invitation link.
     * @return [LinkDestination.Onboarding] if a valid target URL is successfully extracted from the `uri`'s fragment,
     *         otherwise [LinkDestination.NoDestination].
     */
    private fun handleInviteLink(uri: Uri): LinkDestination {
        // Extract the server URL from the fragment #url=http://homeassistant.local:8123 and make it queryParam in a fake URI
        val serverURL = "https://?${uri.encodedFragment.orEmpty()}".toUri().takeIf {
            // If for some reason the fragment contains an opaque URI getQueryParameter will throw
            !it.isOpaque
        }?.getQueryParameter("url")

        return if (serverURL == null) {
            FailFast.fail { "Deep link does not contains a valid URL to a server ($uri)" }
            LinkDestination.NoDestination
        } else {
            LinkDestination.Onboarding(serverURL)
        }
    }

    /**
     * Handles redirect links from `https://my.home-assistant.io/redirect/...`.
     *
     * Transforms the universal link into an internal path format and adds the mobile parameter.
     * Requires a registered server to proceed.
     *
     * @param uri The redirect URI to process.
     * @return [LinkDestination.Webview] with the transformed path, [LinkDestination.ServerPicker] if multiple servers are registered,
     *         or [LinkDestination.NoDestination] if no server is registered or if the mobile flag is already set.
     */
    private suspend fun handleRedirectLink(uri: Uri): LinkDestination {
        if (!requireServerRegistered()) {
            return LinkDestination.NoDestination
        }

        if (uri.getQueryParameter(MOBILE_PARAM) == MOBILE_VALUE) {
            FailFast.fail { "$MOBILE_PARAM flag already present and set to 1, the deep link is already handled" }
            return LinkDestination.NoDestination
        }

        val path = uri.buildUpon()
            // We strip the last / to handle old links created before https://github.com/home-assistant/frontend/pull/25841.
            // A trailing slash is always added by Netlify that is used to host https://my.home-assistant.io/, but
            // the frontend did not support having a trailing slash before https://github.com/home-assistant/frontend/pull/25841.
            // For backward compatibility, we remove the trailing slash here.
            .path(uri.path?.removeSuffix("/"))
            .appendQueryParameter(MOBILE_PARAM, MOBILE_VALUE)
            .build().toString()
            .replaceFirst(BASE_MY_REDIRECT_URL, INTERNAL_MY_REDIRECT_PREFIX)

        return webviewDestination(FrontendTarget.Path(path))
    }

    /**
     * Handles navigate deep links from `homeassistant://navigate/...`.
     *
     * Server selection (in order of precedence):
     * - `server_id=<id>`: Uses the server with that stable id
     * - `server=<name>`: Searches for a server with matching friendly name (case-insensitive)
     * - No parameter or `server=default`: Uses the active server
     *
     * A root-level `more-info-entity-id=<entity>` opens that entity's more-info dialog.
     *
     * @param uri The navigate URI to process.
     * @return [LinkDestination.Webview] with the resolved target and serverId, or
     *         [LinkDestination.NoDestination] if no server is registered.
     */
    private suspend fun handleNavigateLink(uri: Uri): LinkDestination {
        if (!requireServerRegistered()) {
            return LinkDestination.NoDestination
        }

        val serverId = resolveNavigateServerId(uri)

        // A root-level `more-info-entity-id` maps to FrontendTarget.EntityMoreInfo, which keeps the
        // server-version handling (URL query parameter on HA 2025.6+, JavaScript dispatch on older
        // servers). Anything else is a plain path/URL.
        val moreInfoEntityId = uri.getQueryParameter(MORE_INFO_ENTITY_ID_PARAM)?.takeIf { it.isNotBlank() }
        val target = if (moreInfoEntityId != null && uri.isNavigateRoot()) {
            FrontendTarget.EntityMoreInfo(moreInfoEntityId)
        } else {
            FrontendTarget.Path(uri.toString())
        }
        return webviewDestination(target, serverId)
    }

    /**
     * Resolves the target server for a navigate link: a stable `server_id` takes precedence,
     * otherwise the `server` friendly-name lookup (`default`/absent uses the active server).
     */
    private suspend fun resolveNavigateServerId(uri: Uri): Int? {
        uri.getQueryParameter(SERVER_ID_PARAM)?.toIntOrNull()?.let { return it }
        val serverName = uri.getQueryParameter(SERVER_PARAM).takeIf { !it.isNullOrBlank() }
        return when (serverName) {
            "default", null -> serverManager.getServer()?.id
            else -> serverManager.servers().find { it.friendlyName.equals(serverName, ignoreCase = true) }?.id
        }
    }

    private fun Uri.isNavigateRoot(): Boolean = path.isNullOrEmpty() || path == "/"

    private suspend fun requireServerRegistered(): Boolean {
        return serverManager.isRegistered().also { registered ->
            if (!registered) {
                Timber.w("No server registered, cannot handle deep link")
            }
        }
    }
}
