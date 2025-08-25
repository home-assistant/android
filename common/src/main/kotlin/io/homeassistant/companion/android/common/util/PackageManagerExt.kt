package io.homeassistant.companion.android.common.util

import android.content.pm.PackageManager
import android.os.Build

/**
 * Checks if the current device is an Android Automotive OS device.
 *
 * @return `true` if the device is an Android Automotive OS device, `false` otherwise.
 */
fun PackageManager.isAutomotive(): Boolean {
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)
}
