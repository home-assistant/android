package io.homeassistant.companion.android.common.data.network

interface WifiHelper {

    companion object {
        const val BSSID_PREFIX = "BSSID:"
        const val INVALID_BSSID = "02:00:00:00:00:00"
    }

    /** Returns if the device exposes Wi-Fi adapter(s) to apps. To check if Wi-Fi is used, see [isUsingWifi]. */
    fun hasWifi(): Boolean

    /** Returns if the active data connection is using Wi-Fi */
    fun isUsingWifi(): Boolean

    /** Returns if the active data connection is using one of the provided Wi-Fi networks */
    fun isUsingSpecificWifi(networks: List<String>): Boolean
    fun getWifiSsid(): String?
    fun getWifiBssid(): String?
}
