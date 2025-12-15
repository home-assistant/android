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
import timber.log.Timber

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
        var firstEmission = true
        var lastEmittedState: NetworkState? = null

        fun emitStateIfChanged() {
            val newState = getCurrentNetworkState(serverConfig, firstEmission)
            if (newState != lastEmittedState) {
                trySend(newState)
                lastEmittedState = newState
                firstEmission = false
            }
        }

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = emitStateIfChanged()

            override fun onLost(network: Network) = emitStateIfChanged()

            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) =
                emitStateIfChanged()
        }

        connectivityManager.registerNetworkCallback(networkRequest, callback)

        // Emit initial status
        emitStateIfChanged()

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
     * 4. If network exists but not validated:
     *    - If we have an external URL configured -> [NetworkState.READY_REMOTE] (assume LAN-only network)
     *    - Otherwise -> [NetworkState.CONNECTING] (wait for validation)
     *
     * Note: Both internal and validated may be true, but internal takes precedence
     * as it typically represents a faster and preferred path.
     *
     * @param isFirstCheck True if this is the first network check, false for subsequent checks.
     *                     On the first check, we're more lenient for LAN-only networks.
     */
    private fun getCurrentNetworkState(serverConfig: ServerConnectionInfo, isFirstCheck: Boolean): NetworkState {
        val hasActiveNetwork = networkHelper.hasActiveNetwork()
        val isInternal = serverConfig.isInternal(requiresUrl = false)
        val isValidated = networkHelper.isNetworkValidated()
        val hasExternalUrl = !serverConfig.externalUrl.isNullOrBlank()

        Timber.d(
            "[DEBUG #6099] Network state check: hasActiveNetwork=$hasActiveNetwork, " +
                "isInternal=$isInternal, isValidated=$isValidated, " +
                "hasExternalUrl=$hasExternalUrl, isFirstCheck=$isFirstCheck, " +
                "internalSSID=${serverConfig.internalSsids}",
        )

        return when {
            !hasActiveNetwork -> {
                Timber.d("[DEBUG #6099] Result: UNAVAILABLE (no active network)")
                NetworkState.UNAVAILABLE
            }
            isInternal -> {
                Timber.d("[DEBUG #6099] Result: READY_LOCAL (internal network detected)")
                NetworkState.READY_LOCAL
            }
            isValidated -> {
                Timber.d("[DEBUG #6099] Result: READY_REMOTE (network validated)")
                NetworkState.READY_REMOTE
            }
            hasExternalUrl -> {
                // Fix for issue #6099: On LAN-only networks without internet access,
                // NET_CAPABILITY_VALIDATED will never be true. If we have an active network
                // and an external URL configured, assume the network is ready and attempt connection.
                // The WebView/API layer will handle actual connectivity failures.
                Timber.d(
                    "[DEBUG #6099] Result: READY_REMOTE (LAN-only network assumed - " +
                        "network exists but not validated, external URL configured)",
                )
                NetworkState.READY_REMOTE
            }
            else -> {
                Timber.d("[DEBUG #6099] Result: CONNECTING (waiting for network validation)")
                NetworkState.CONNECTING
            }
        }
    }
}
