package io.homeassistant.companion.android.common.data.wifi

interface WifiHelper {
    /** Returns if the active data connection is using Wi-Fi */
    fun isUsingWifi(): Boolean
    fun getWifiSsid(): String?
    fun getWifiBssid(): String?
}
