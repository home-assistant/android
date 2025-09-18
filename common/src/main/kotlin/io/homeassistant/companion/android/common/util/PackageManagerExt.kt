package io.homeassistant.companion.android.common.util

import android.content.pm.PackageManager

/**
 * Checks if the current device is an Android Automotive OS device.
 *
 * @return `true` if the device is an Android Automotive OS device, `false` otherwise.
 */
fun PackageManager.isAutomotive(): Boolean {
    return hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)
}
