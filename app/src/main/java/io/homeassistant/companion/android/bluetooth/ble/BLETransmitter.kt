package io.homeassistant.companion.android.bluetooth.ble

data class BLETransmitter(
    var id1: String,
    var id2: String,
    var id3: String,
    var transmitting: Boolean = false,
    var state: String
)
