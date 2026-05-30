package io.homeassistant.companion.android.frontend.improv

/**
 * Reports the device's Bluetooth-related capabilities.
 *
 * Exposed as an interface so call sites (e.g. the config/get response, the improv scan flow)
 * can stay testable without Robolectric.
 */
fun interface BluetoothCapabilities {

    /**
     * @return `true` when the device declares Bluetooth Low Energy support
     *   ([android.content.pm.PackageManager.FEATURE_BLUETOOTH_LE]).
     */
    fun hasBluetoothLe(): Boolean
}
