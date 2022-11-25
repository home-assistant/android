package io.homeassistant.companion.android.common.data.wifi

import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import javax.inject.Inject

@Suppress("DEPRECATION")
class WifiHelperImpl @Inject constructor(
    private val connectivityManager: ConnectivityManager,
    private val wifiManager: WifiManager
) : WifiHelper {
    override fun isUsingWifi(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            connectivityManager.activeNetwork?.let {
                connectivityManager
                    .getNetworkCapabilities(it)
                    ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
            } ?: false
        } else {
            connectivityManager.activeNetworkInfo?.isConnected == true &&
                connectivityManager.activeNetworkInfo?.type == ConnectivityManager.TYPE_WIFI
        }

    override fun getWifiSsid(): String? =
        wifiManager.connectionInfo.ssid

    override fun getWifiBssid(): String? =
        wifiManager.connectionInfo.bssid
}
