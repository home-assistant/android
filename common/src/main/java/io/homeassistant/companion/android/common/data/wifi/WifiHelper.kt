package io.homeassistant.companion.android.common.data.wifi

interface WifiHelper {
    fun getWifiSsid(): String?
    fun getWifiBssid(): String?
}
