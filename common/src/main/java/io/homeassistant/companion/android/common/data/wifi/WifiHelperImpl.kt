package io.homeassistant.companion.android.common.data.wifi

import android.net.wifi.WifiManager
import javax.inject.Inject

class WifiHelperImpl @Inject constructor(
    private val wifiManager: WifiManager
) : WifiHelper {
    override fun getWifiSsid(): String {
        return wifiManager.connectionInfo.ssid
    }
}
