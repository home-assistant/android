package io.homeassistant.companion.android.common.data.network

import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.NetworkInfo
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A utility class providing access to current network transport capabilities.
 * It wraps [ConnectivityManager] to determine if the device is connected
 * via a specific transport type.
 *
 * It offers checks using [NetworkCapabilities] (for Android Marshmallow/API 23 and above)
 * and provides fallback logic using [NetworkInfo] for older devices (pre-Marshmallow/API < 23).
 */
@Singleton
internal class NetworkCapabilitiesChecker @Inject constructor(private val connectivityManager: ConnectivityManager) {

    /**
     * Checks if the active network has the specified capability.
     *
     * For Android M (API 23) and above, checks the active network's capabilities.
     * For older versions, always returns `false`.
     *
     * @param capability The capability constant from [NetworkCapabilities] (e.g., [NetworkCapabilities.NET_CAPABILITY_INTERNET])
     * @return `true` if the active network has the capability, `false` otherwise
     */
    fun hasCapability(capability: Int): Boolean = getActiveNetworkCapabilities()?.hasCapability(capability)
        ?: false

    /**
     * Checks if the active network uses the specified transport type.
     *
     * For Android M (API 23) and above, checks the active network's transport.
     * For older versions, falls back to checking [NetworkInfo] type.
     *
     * @param transport The transport constant from [NetworkCapabilities] (e.g., [NetworkCapabilities.TRANSPORT_WIFI])
     * @return `true` if the active network uses the transport, `false` otherwise
     */
    fun hasTransport(transport: Int): Boolean = getActiveNetworkCapabilities()?.hasTransport(transport)
        ?: fallbackHasTransport(transport)

    private fun getActiveNetworkCapabilities(): NetworkCapabilities? = connectivityManager.activeNetwork?.let {
        connectivityManager.getNetworkCapabilities(it)
    }

    private fun fallbackHasTransport(transport: Int): Boolean {
        val info: NetworkInfo = connectivityManager.activeNetworkInfo ?: return false
        if (!info.isConnected) return false

        return when (transport) {
            NetworkCapabilities.TRANSPORT_WIFI -> info.type == ConnectivityManager.TYPE_WIFI
            NetworkCapabilities.TRANSPORT_CELLULAR -> info.type == ConnectivityManager.TYPE_MOBILE
            NetworkCapabilities.TRANSPORT_ETHERNET -> info.type == ConnectivityManager.TYPE_ETHERNET
            NetworkCapabilities.TRANSPORT_VPN -> info.type == ConnectivityManager.TYPE_VPN
            else -> false
        }
    }
}
