package io.homeassistant.companion.android.common.data.network

import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import io.homeassistant.companion.android.common.util.SdkVersion
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
        val formattedSsid = getWifiSsid()
        val formattedBssid = getWifiBssid()
        return (
            formattedSsid != null &&
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

    override fun getWifiSsid(): String? {
        val rawSsid = wifiManager?.connectionInfo?.ssid ?: return null // Deprecated but callbacks aren't instant
        return rawSsid
            .takeUnless {
                SdkVersion.isAtLeast(Build.VERSION_CODES.R) && it == WifiManager.UNKNOWN_SSID
            }
            ?.removeSurrounding("\"")
    }

    override fun getWifiBssid(): String? =
        wifiManager?.connectionInfo?.bssid // Deprecated but callback doesn't provide BSSID info instantly
}
