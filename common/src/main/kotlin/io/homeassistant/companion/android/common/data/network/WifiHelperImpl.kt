package io.homeassistant.companion.android.common.data.network

import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import javax.inject.Inject

@Suppress("DEPRECATION")
class WifiHelperImpl @Inject constructor(
    private val connectivityManager: ConnectivityManager,
    private val wifiManager: WifiManager?,
) : WifiHelper {
    override fun hasWifi(): Boolean = wifiManager != null

    override fun isUsingWifi(): Boolean = connectivityManager.activeNetwork?.let {
        connectivityManager
            .getNetworkCapabilities(it)
            ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
    } ?: false

    override fun isUsingSpecificWifi(networks: List<String>): Boolean {
        if (networks.isEmpty()) return false
        val formattedSsid = getWifiSsid()?.removeSurrounding("\"")
        val formattedBssid = getWifiBssid()
        return (
            formattedSsid != null &&
                (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || formattedSsid !== WifiManager.UNKNOWN_SSID) &&
                formattedSsid in networks
            ) ||
            (
                formattedBssid != null &&
                    formattedBssid != WifiHelper.INVALID_BSSID &&
                    networks.any {
                        it.startsWith(WifiHelper.BSSID_PREFIX) &&
                            it.removePrefix(WifiHelper.BSSID_PREFIX).equals(formattedBssid, ignoreCase = true)
                    }
                )
    }

    override fun getWifiSsid(): String? =
        wifiManager?.connectionInfo?.ssid // Deprecated but callback doesn't provide SSID info instantly

    override fun getWifiBssid(): String? =
        wifiManager?.connectionInfo?.bssid // Deprecated but callback doesn't provide BSSID info instantly
}
