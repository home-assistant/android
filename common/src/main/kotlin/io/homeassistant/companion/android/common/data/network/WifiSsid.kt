package io.homeassistant.companion.android.common.data.network

import android.net.wifi.WifiManager
import android.os.Build
import io.homeassistant.companion.android.common.util.SdkVersion

/**
 * Returns whether this value is [WifiManager.UNKNOWN_SSID] on Android 11 or newer.
 *
 * The receiver must be an unquoted SSID.
 *
 * Earlier Android versions deliberately return `false` to preserve the app's existing SSID handling.
 */
fun String.isUnavailableSsid(): Boolean =
    SdkVersion.isAtLeast(Build.VERSION_CODES.R) && this == WifiManager.UNKNOWN_SSID
