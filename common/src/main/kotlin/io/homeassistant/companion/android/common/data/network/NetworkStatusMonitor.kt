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
 * Tracks network changes using ConnectivityManager callbacks.
 * Automatically handles callback registration/cleanup.
 */
@Singleton
class NetworkStatusMonitorImpl @Inject constructor(
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

    private fun getCurrentNetworkState(serverConfig: ServerConnectionInfo): NetworkState = when {
        !networkHelper.hasActiveNetwork() -> NetworkState.UNAVAILABLE
        serverConfig.isInternal(requiresUrl = false) -> NetworkState.READY_LOCAL
        networkHelper.isNetworkValidated() -> NetworkState.READY_REMOTE
        else -> NetworkState.CONNECTING
    }
}

enum class NetworkState {
    READY_LOCAL,
    READY_REMOTE,
    CONNECTING,
    UNAVAILABLE,
}
