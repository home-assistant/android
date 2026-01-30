package io.homeassistant.companion.android.common.data.network

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import io.homeassistant.companion.android.common.data.servers.ServerConnectionStateProvider
import io.homeassistant.companion.android.util.isPubliclyAccessible
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapLatest
import timber.log.Timber

interface NetworkStatusMonitor {
    /**
     * Observes network state changes. Emits current state immediately.
     * @return Flow of NetworkState updates
     */
    fun observeNetworkStatus(connectionStateProvider: ServerConnectionStateProvider): Flow<NetworkState>
}

/**
 * Represents the current network connectivity state, especially relevant to server access.
 *
 * The `READY_*` states indicate the app can proceed with connection attempts.
 * Different states help with debugging and logging to understand the network conditions.
 */
enum class NetworkState {
    /**
     * Device is connected to the configured internal/home network (matched by SSID, Ethernet, or VPN).
     * The server can be reached via its internal URL. Network validation status is irrelevant.
     */
    READY_INTERNAL,

    /**
     * Network is available and validated by Android (internet connectivity confirmed).
     * Not on the internal network, so server will be accessed through external/cloud URL.
     */
    READY_NET_VALIDATED,

    /**
     * Network is available but NOT validated (no internet connectivity).
     * However, the configured external URL points to a private/local address
     * (e.g., 192.168.x.x, 10.x.x.x, .local, .home), so connection can proceed.
     *
     * This handles LAN-only networks (e.g., isolated IoT VLANs) where Android's
     * NET_CAPABILITY_VALIDATED is never set because there's no internet access.
     */
    READY_NET_LOCAL,

    /**
     * Network connection exists but has not yet been validated or identified as internal.
     * This is a transitional state while waiting for system validation or SSID checks.
     */
    CONNECTING,

    /**
     * No active network connection is currently available.
     */
    UNAVAILABLE,
}

/**
 * Tracks network changes using ConnectivityManager callbacks.
 * Automatically handles callback registration/cleanup.
 */
@Singleton
internal class NetworkStatusMonitorImpl @Inject constructor(
    private val connectivityManager: ConnectivityManager,
    private val networkHelper: NetworkHelper,
) : NetworkStatusMonitor {

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeNetworkStatus(connectionStateProvider: ServerConnectionStateProvider): Flow<NetworkState> =
        callbackFlow {
            val networkRequest = NetworkRequest.Builder().build()

            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    trySend(Unit)
                }

                override fun onLost(network: Network) {
                    trySend(Unit)
                }

                override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                    trySend(Unit)
                }
            }

            connectivityManager.registerNetworkCallback(networkRequest, callback)
            trySend(Unit) // Emit status at start

            awaitClose {
                connectivityManager.unregisterNetworkCallback(callback)
            }
        }.mapLatest {
            getCurrentNetworkState(connectionStateProvider)
        }.distinctUntilChanged()

    /**
     * Evaluates the current network state relevant to the provided server configuration.
     *
     * Priority of checks:
     * 1. If no active network -> [NetworkState.UNAVAILABLE]
     * 2. If device is on internal network (SSID, VPN, Ethernet match) -> [NetworkState.READY_INTERNAL]
     * 3. If network is validated (internet reachable) -> [NetworkState.READY_NET_VALIDATED]
     * 4. If network exists but not validated:
     *    - If external URL is a private address -> [NetworkState.READY_NET_LOCAL] (LAN-only network)
     *    - Otherwise -> [NetworkState.CONNECTING] (wait for validation)
     *
     * Note: Both internal and validated may be true, but internal takes precedence
     * as it typically represents a faster and preferred path.
     */
    private suspend fun getCurrentNetworkState(connectionStateProvider: ServerConnectionStateProvider): NetworkState {
        val hasActiveNetwork = networkHelper.hasActiveNetwork()
        val isInternal = connectionStateProvider.isInternal(requiresUrl = false)
        val isValidated = networkHelper.isNetworkValidated()

        return when {
            !hasActiveNetwork -> NetworkState.UNAVAILABLE
            isInternal -> NetworkState.READY_INTERNAL
            isValidated -> NetworkState.READY_NET_VALIDATED
            else -> {
                // Check URL only when we need it - when network exists but not validated and not internal
                val urlIsPrivate = try {
                    connectionStateProvider.getExternalUrl()?.let { url ->
                        !url.isPubliclyAccessible()
                    } ?: false
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.e(e, "Failed to check external URL accessibility")
                    false
                }

                if (urlIsPrivate) {
                    // Fix for issue #6099: On LAN-only networks without internet access,
                    // NET_CAPABILITY_VALIDATED will never be true. If we have an active network
                    // and the external URL is a local/private address (e.g., http://192.168.1.100:8123,
                    // http://ha.local, http://ha.home), assume the network is ready and attempt connection.
                    // The WebView/API layer will handle actual connectivity failures.
                    NetworkState.READY_NET_LOCAL
                } else {
                    NetworkState.CONNECTING
                }
            }
        }
    }
}
