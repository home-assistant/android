package io.homeassistant.companion.android.common.data.network

import android.net.NetworkCapabilities
import javax.inject.Inject
import javax.inject.Singleton

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

    override fun isNetworkValidated(): Boolean =
        capabilitiesChecker.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)

    override fun hasActiveNetwork(): Boolean = listOf(
        NetworkCapabilities.TRANSPORT_WIFI,
        NetworkCapabilities.TRANSPORT_ETHERNET,
        NetworkCapabilities.TRANSPORT_CELLULAR,
        NetworkCapabilities.TRANSPORT_VPN,
        NetworkCapabilities.TRANSPORT_BLUETOOTH,
    ).any { capabilitiesChecker.hasTransport(it) }

    override fun isUsingEthernet(): Boolean = capabilitiesChecker.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)

    override fun isUsingVpn(): Boolean = capabilitiesChecker.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
}
