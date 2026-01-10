package io.homeassistant.companion.android.common.data.servers

import dagger.assisted.AssistedFactory
import io.homeassistant.companion.android.common.BuildConfig
import io.homeassistant.companion.android.common.data.integration.IntegrationException
import io.homeassistant.companion.android.common.data.integration.NoUrlAvailableException
import io.homeassistant.companion.android.database.server.ServerConnectionInfo
import java.net.URL
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import okhttp3.HttpUrl
import timber.log.Timber

/**
 * Extracts the first URL from a [UrlState] Flow, or returns null if insecure.
 *
 * @param onNullMessage optional message logged when the state is not [UrlState.HasUrl]
 * @return the URL if available, or `null` if the state is [UrlState.InsecureState]
 */
suspend fun Flow<UrlState>.firstUrlOrNull(onNullMessage: (() -> String)? = null): URL? {
    val state = first()
    return if (state is UrlState.HasUrl) {
        state.url
    } else {
        onNullMessage?.let { Timber.d(it()) }
        null
    }
}

/**
 * Tries to execute an action on multiple URLs in order, returning the first successful result.
 *
 * This function iterates through the provided URLs and attempts to execute the given action
 * on each one. If an action succeeds, its result is returned immediately. If an action fails
 * with an exception, the next URL is tried. If all URLs fail, an [IntegrationException] is
 * thrown containing the first exception encountered.
 *
 * @param urls the list of URLs to try, in priority order
 * @param requestName a descriptive name for the request, used in error messages and logging
 * @param action the suspend function to execute on each URL
 * @return the result from the first successful URL
 * @throws NoUrlAvailableException if [urls] is empty
 * @throws IntegrationException if all URLs fail, with the first exception as the cause
 */
suspend fun <T> tryOnUrls(urls: List<HttpUrl>, requestName: String, action: suspend (HttpUrl) -> T): T {
    if (urls.isEmpty()) {
        throw NoUrlAvailableException()
    }

    var firstException: Exception? = null
    for (url in urls) {
        try {
            return action(url)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Failed $requestName ${if (BuildConfig.DEBUG) "on $url" else ""}")
            if (firstException == null) firstException = e
        }
    }

    throw IntegrationException(
        "All URLs failed for $requestName",
        firstException ?: Exception("Error calling integration request $requestName"),
    )
}

/**
 * Represents the current URL state based on network conditions and security settings.
 */
sealed interface UrlState {
    /**
     * A URL is available and safe to use.
     *
     * This state is emitted when:
     * - The URL uses HTTPS, or
     * - The device is on an home network (HTTP is acceptable internally), or
     * - The user has explicitly allowed insecure connections
     *
     * @property url the server URL to use, may be `null` if no valid URL is configured
     */
    data class HasUrl(val url: URL?) : UrlState

    /**
     * The connection would be insecure and is not allowed.
     *
     * This state is emitted when the URL uses HTTP, the device is not on an home network,
     * and the user has not allowed insecure connections.
     */
    data object InsecureState : UrlState
}

/**
 * Provides server connection state information based on current network conditions.
 *
 * This provider determines the appropriate URL to use based on the current network state
 * (internal vs external), manages security checks for connections, and emits reactive
 * updates when network conditions change.
 *
 * @see ServerConnectionStateProviderFactory for creating instances
 */
interface ServerConnectionStateProvider {

    /**
     * Indicates if the device's current connection should be treated as on the home network.
     * When on the home network, the internal URL should be used.
     *
     * Home network detection is based on the server's configuration:
     * - Connected to a configured Wi-Fi SSID (requires location permission)
     * - Connected via Ethernet (if enabled in settings)
     * - Connected via VPN (if enabled in settings)
     *
     * @param requiresUrl whether a valid internal URL must be configured. Set to `true` when
     *   determining which URL to use, `false` when just checking network state.
     * @return `true` if the device is currently on the home network
     */
    suspend fun isInternal(requiresUrl: Boolean = true): Boolean

    /**
     * Returns the external URL configured for this server. The URL can be
     * the cloud one or the external one depending on the user preferences.
     *
     * @return the external URL, or `null` if not configured or invalid
     */
    suspend fun getExternalUrl(): URL?

    /**
     * Gets all API URLs for webhook communication, ordered by priority.
     *
     * The order depends on whether the device is on an home network:
     * - Internal: internal URL, then cloudhook, then external
     * - External: cloudhook first (if available), then external
     *
     * @return list of valid webhook API URLs, empty if not registered (no webhook ID)
     */
    suspend fun getApiUrls(): List<HttpUrl>

    /**
     * Returns security information about the current connection state.
     *
     * @return [SecurityState] containing home network status, home setup status,
     *   and location permission state
     */
    suspend fun getSecurityState(): SecurityState

    /**
     * Checks if it's safe to send authentication credentials to the given URL.
     *
     * Credentials are safe to send when:
     * - The URL uses HTTPS (always safe), or
     * - The URL belongs to this server AND the device is on an home network, or
     * - The URL belongs to this server AND insecure connections are explicitly allowed
     *
     * @param url the URL to check
     * @return `true` if credentials can safely be sent to this URL
     */
    suspend fun canSafelySendCredentials(url: String): Boolean

    /**
     * Returns a Flow that emits the current URL state based on network conditions and security.
     *
     * The Flow reacts to changes in:
     * - Location state (permission and services, affecting SSID detection)
     * - Network connectivity (Wi-Fi SSID, Ethernet, VPN)
     * - Server connection configuration changes in the database
     *
     * @param isInternalOverride optional callback to override home network detection.
     *   When provided, this function is called with the current [ServerConnectionInfo] to
     *   determine if the device should be treated as internal.
     * @return a Flow of [UrlState] that emits whenever conditions change
     * @see UrlState.HasUrl for when a URL is available
     * @see UrlState.InsecureState for when the connection would be insecure
     */
    fun urlFlow(isInternalOverride: ((ServerConnectionInfo) -> Boolean)? = null): Flow<UrlState>
}

@AssistedFactory
internal interface ServerConnectionStateProviderFactory {
    /**
     * Creates a [ServerConnectionStateProviderImpl] for the specified server.
     *
     * @param serverId the ID of the server to create a provider for
     * @return a new provider instance bound to the specified server
     */
    fun create(serverId: Int): ServerConnectionStateProviderImpl
}
