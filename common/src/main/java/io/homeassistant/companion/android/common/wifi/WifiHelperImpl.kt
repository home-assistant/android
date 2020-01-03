package io.homeassistant.companion.android.common.wifi

import android.net.wifi.WifiManager
import io.homeassistant.companion.android.data.wifi.WifiHelper

class WifiHelperImpl constructor(
    private val wifiManager: WifiManager
) : WifiHelper {
    override fun getWifiSsid(): String {
        return wifiManager.connectionInfo.ssid
    }
}
