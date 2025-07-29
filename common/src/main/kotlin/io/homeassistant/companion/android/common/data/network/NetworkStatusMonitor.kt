package io.homeassistant.companion.android.common.data.network

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import io.homeassistant.companion.android.database.server.ServerConnectionInfo
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

interface NetworkStatusMonitor {
    /**
     * Observes network state changes. Emits current state immediately.
     * @return Flow of NetworkState updates
     */
    fun observeNetworkStatus(serverConfig: ServerConnectionInfo): Flow<NetworkState>
}

/**
 * Represents the current network connectivity state, especially relevant to server access.
 */
enum class NetworkState {
    /**
     * A local network is available and the current connection matches internal criteria (e.g., SSID, Ethernet, VPN).
     * This typically indicates the server can be reached via its internal URL.
     */
    READY_LOCAL,

    /**
     * Network is available and validated (e.g., internet is reachable),
     * but we are not considered to be on the internal network.
     * Server will be accessed through external/cloud URL.
     */
    READY_REMOTE,

    /**
     * Network connection exists but has not yet been validated or identified as internal.
     * This is a transitional state while we wait for system validation or SSID checks.
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

    override fun observeNetworkStatus(serverConfig: ServerConnectionInfo): Flow<NetworkState> = callbackFlow {
        val networkRequest = NetworkRequest.Builder().build()

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(getCurrentNetworkState(serverConfig))
            }

            override fun onLost(network: Network) {
                trySend(getCurrentNetworkState(serverConfig))
            }

            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                trySend(getCurrentNetworkState(serverConfig))
            }
        }

        connectivityManager.registerNetworkCallback(networkRequest, callback)
        trySend(getCurrentNetworkState(serverConfig)) // Emit status at start

        awaitClose {
            connectivityManager.unregisterNetworkCallback(callback)
        }
    }.distinctUntilChanged()

    /**
     * Evaluates the current network state relevant to the provided server configuration.
     *
     * Priority of checks:
     * 1. If no active network -> [NetworkState.UNAVAILABLE]
     * 2. If device is considered internal (SSID, VPN, Ethernet match) -> [NetworkState.READY_LOCAL]
     * 3. If network is validated (but not internal) -> [NetworkState.READY_REMOTE]
     * 4. Otherwise -> [NetworkState.CONNECTING]
     *
     * Note: Both internal and validated may be true, but internal takes precedence
     * as it typically represents a faster and preferred path.
     */
    private fun getCurrentNetworkState(serverConfig: ServerConnectionInfo): NetworkState = when {
        !networkHelper.hasActiveNetwork() -> NetworkState.UNAVAILABLE
        serverConfig.isInternal(requiresUrl = false) -> NetworkState.READY_LOCAL
        networkHelper.isNetworkValidated() -> NetworkState.READY_REMOTE
        else -> NetworkState.CONNECTING
    }
}
