package io.homeassistant.companion.android.launch.link

import android.net.Uri
import androidx.core.net.toUri
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.util.FailFast
import javax.inject.Inject
import timber.log.Timber

private const val REDIRECT_URL_PATH = "/redirect/"
private const val INVITE_URL_PATH = "/invite/"

private const val MY_BASE_DOMAIN = "my.home-assistant.io"
private const val BASE_MY_REDIRECT_URL = "https://$MY_BASE_DOMAIN$REDIRECT_URL_PATH"

private const val DEEP_LINK_SCHEME = "homeassistant"

private const val INTERNAL_MY_REDIRECT_PREFIX = "_my_redirect/"

private const val MOBILE_PARAM = "mobile"
private const val MOBILE_VALUE = "1"

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
     * Open a WebView with the given [url].
     */
    data class Webview(val path: String) : LinkDestination

    /**
     * Nowhere to go from this link.
     */
    object NoDestination : LinkDestination
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
    fun handleLink(uri: Uri): LinkDestination
}

class LinkHandlerImpl @Inject constructor(private val serverManager: ServerManager) : LinkHandler {

    override fun handleLink(uri: Uri): LinkDestination {
        return when (uri.scheme) {
            "https" -> handleUniversalLink(uri)
            DEEP_LINK_SCHEME -> handleDeepLink(uri)
            else -> {
                FailFast.fail {
                    "Invalid link scheme: $uri"
                }
                LinkDestination.NoDestination
            }
        }
    }

    private fun handleUniversalLink(uri: Uri): LinkDestination {
        val path = uri.path.orEmpty()

        if (uri.host != MY_BASE_DOMAIN) {
            FailFast.fail {
                "Invalid deep link host: $uri should be $MY_BASE_DOMAIN"
            }
            return LinkDestination.NoDestination
        }
        return when {
            path.startsWith(REDIRECT_URL_PATH) -> handleNavigateLink(uri)
            path.startsWith(INVITE_URL_PATH) -> handleInviteLink(uri)
            else -> {
                FailFast.fail { "Unknown or invalid universal link: $uri" }
                LinkDestination.NoDestination
            }
        }
    }

    private fun handleDeepLink(uri: Uri): LinkDestination {
        return when {
            uri.host == INVITE_URL_PATH.removeSurrounding("/") -> handleInviteLink(uri)
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

    private fun handleNavigateLink(uri: Uri): LinkDestination {
        if (!serverManager.isRegistered()) {
            Timber.w("No server registered, cannot handle deep link")
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

        return LinkDestination.Webview(path)
    }
}
