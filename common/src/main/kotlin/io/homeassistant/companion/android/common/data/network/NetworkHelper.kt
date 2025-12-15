package io.homeassistant.companion.android.common.data.network

import android.net.NetworkCapabilities
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

interface NetworkHelper {
    /** Returns if network is validated for internet access */
    fun isNetworkValidated(): Boolean

    /** Returns if any network connection exists */
    fun hasActiveNetwork(): Boolean

    /** Returns if the active data connection is using ethernet */
    fun isUsingEthernet(): Boolean

    /** Returns if the active data connection is a VPN connection */
    fun isUsingVpn(): Boolean
}

@Singleton
internal class NetworkHelperImpl @Inject constructor(private val capabilitiesChecker: NetworkCapabilitiesChecker) :
    NetworkHelper {

    override fun isNetworkValidated(): Boolean {
        val isValidated = capabilitiesChecker.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        Timber.d("[DEBUG #6099] NetworkHelper.isNetworkValidated: $isValidated")
        return isValidated
    }

    override fun hasActiveNetwork(): Boolean {
        val transports = mapOf(
            "WIFI" to NetworkCapabilities.TRANSPORT_WIFI,
            "ETHERNET" to NetworkCapabilities.TRANSPORT_ETHERNET,
            "CELLULAR" to NetworkCapabilities.TRANSPORT_CELLULAR,
            "VPN" to NetworkCapabilities.TRANSPORT_VPN,
            "BLUETOOTH" to NetworkCapabilities.TRANSPORT_BLUETOOTH,
        )

        val activeTransports = transports.filter { (_, transport) ->
            capabilitiesChecker.hasTransport(transport)
        }.keys

        val hasActive = activeTransports.isNotEmpty()
        Timber.d("[DEBUG #6099] NetworkHelper.hasActiveNetwork: $hasActive (active transports: $activeTransports)")
        return hasActive
    }

    override fun isUsingEthernet(): Boolean = capabilitiesChecker.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)

    override fun isUsingVpn(): Boolean = capabilitiesChecker.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
}
