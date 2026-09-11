package io.homeassistant.companion.android.common.data.network

import android.net.wifi.WifiManager
import android.os.Build
import io.homeassistant.companion.android.common.util.SdkVersion

/**
 * Returns whether [ssid] is [WifiManager.UNKNOWN_SSID] on Android 11 or newer.
 *
 * Android returns the constant instance when the SSID is unavailable. A real network named
 * `<unknown ssid>` is represented by a distinct string, so identity comparison is intentional.
 *
 * Earlier Android versions deliberately return `false` to preserve the app's existing SSID handling.
 */
@Suppress("AvoidReferentialEquality")
fun isUnavailableSsid(ssid: String): Boolean =
    SdkVersion.isAtLeast(Build.VERSION_CODES.R) && ssid === WifiManager.UNKNOWN_SSID
