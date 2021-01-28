package io.homeassistant.companion.android.bluetooth

data class BluetoothDevice(
    val address: String,
    val name: String,
    val paired: Boolean,
    val connected: Boolean
)
