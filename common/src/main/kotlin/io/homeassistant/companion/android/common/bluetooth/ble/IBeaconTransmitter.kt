package io.homeassistant.companion.android.common.bluetooth.ble

data class IBeaconTransmitter(
    override var uuid: String,
    override var major: String,
    override var minor: String,
    var transmitting: Boolean = false,
    var transmitRequested: Boolean = false,
    var state: String,
    var transmitPowerSetting: String,
    var measuredPowerSetting: Int,
    var advertiseModeSetting: String,
    var onlyTransmitOnHomeWifiSetting: Boolean = false,
    var restartRequired: Boolean = false,
    val manufacturer: Int = 0x004c,
    val beaconLayout: String = "m:2-3=0215,i:4-19,i:20-21,i:22-23,p:24-24",
) : IBeaconNameFormat
