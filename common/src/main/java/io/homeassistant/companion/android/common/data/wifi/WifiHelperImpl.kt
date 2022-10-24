package io.homeassistant.companion.android.common.data.wifi

import android.net.wifi.WifiManager
import javax.inject.Inject

@Suppress("DEPRECATION")
class WifiHelperImpl @Inject constructor(
    private val wifiManager: WifiManager
) : WifiHelper {
    override fun getWifiSsid(): String? =
        wifiManager.connectionInfo.ssid

    override fun getWifiBssid(): String? =
        wifiManager.connectionInfo.bssid
}
