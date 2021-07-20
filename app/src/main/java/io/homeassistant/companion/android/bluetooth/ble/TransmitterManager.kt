package io.homeassistant.companion.android.bluetooth.ble

import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseSettings
import android.content.Context
import org.altbeacon.beacon.Beacon
import org.altbeacon.beacon.BeaconParser
import org.altbeacon.beacon.BeaconTransmitter
import java.util.UUID

object TransmitterManager {
    private lateinit var physicalTransmitter: BeaconTransmitter
    private lateinit var beacon: Beacon

    private fun buildBeacon(haTransmitterI: io.homeassistant.companion.android.bluetooth.ble.IBeaconTransmitter): Beacon {
        val builder = Beacon.Builder()
        builder.setTxPower(-59)
        builder.setId1(haTransmitterI.uuid)
        builder.setId2(haTransmitterI.major)
        builder.setId3(haTransmitterI.minor)
        builder.setManufacturer(haTransmitterI.manufacturer)
        beacon = builder.build()
        return beacon
    }

    private fun shouldStartTransmitting(haTransmitter: IBeaconTransmitter): Boolean {
        return validateInputs(haTransmitter) && (!this::physicalTransmitter.isInitialized || !physicalTransmitter.isStarted || haTransmitter.restartRequired)
    }

    private fun validateInputs(haTransmitter: IBeaconTransmitter): Boolean {
        try {
            UUID.fromString(haTransmitter.uuid)
            if (haTransmitter.major.toInt() < 0 || haTransmitter.major.toInt() > 65535 || haTransmitter.minor.toInt() < 0 || haTransmitter.minor.toInt() > 65535)
                throw IllegalArgumentException("Invalid Major or Minor")
        } catch (e: IllegalArgumentException) {
            stopTransmitting(haTransmitter)
            haTransmitter.state = "Invalid parameters, check UUID, Major and Minor"
            return false
        }
        return true
    }

    @Synchronized
    fun startTransmitting(context: Context, haTransmitter: io.homeassistant.companion.android.bluetooth.ble.IBeaconTransmitter) {
        if (!shouldStartTransmitting(haTransmitter)) {
            return
        }
        if (haTransmitter.restartRequired && this::physicalTransmitter.isInitialized) {
            stopTransmitting(haTransmitter)
        }
        if (!this::physicalTransmitter.isInitialized) {
            val parser = BeaconParser().setBeaconLayout(haTransmitter.beaconLayout)
            physicalTransmitter = BeaconTransmitter(context, parser)
            // this setting is how frequently we emit, low power mode is 1hz, could be a setting to make faster.
            physicalTransmitter.advertiseMode = AdvertiseSettings.ADVERTISE_MODE_LOW_POWER
        }
        val bluetoothOn = BluetoothAdapter.getDefaultAdapter().isEnabled
        if (bluetoothOn) {
            val beacon = buildBeacon(haTransmitter)
            if (!physicalTransmitter.isStarted) {
                physicalTransmitter.advertiseTxPowerLevel = getPowerLevel(haTransmitter)
                physicalTransmitter.startAdvertising(
                    beacon,
                    object : AdvertiseCallback() {
                        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
                            haTransmitter.transmitting = true
                            haTransmitter.state = "Transmitting"
                        }

                        override fun onStartFailure(errorCode: Int) {
                            if (errorCode != ADVERTISE_FAILED_ALREADY_STARTED) {
                                haTransmitter.uuid = ""
                                haTransmitter.major = ""
                                haTransmitter.minor = ""
                                haTransmitter.state = "Unable to transmit"
                                haTransmitter.transmitting = false
                            } else {
                                haTransmitter.transmitting = true
                                haTransmitter.state = "Transmitting"
                            }
                        }
                    }
                )
            }
        } else {
            stopTransmitting(haTransmitter)
        }
    }

    private fun getPowerLevel(haTransmitter: IBeaconTransmitter) =
        when (haTransmitter.transmitPowerSetting) {
            "high" -> AdvertiseSettings.ADVERTISE_TX_POWER_HIGH
            "medium" -> AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM
            "low" -> AdvertiseSettings.ADVERTISE_TX_POWER_LOW
            else -> AdvertiseSettings.ADVERTISE_TX_POWER_ULTRA_LOW
        }

    fun stopTransmitting(haTransmitter: io.homeassistant.companion.android.bluetooth.ble.IBeaconTransmitter) {
        if (haTransmitter.transmitting && this::physicalTransmitter.isInitialized) {
            if (physicalTransmitter.isStarted)
                physicalTransmitter.stopAdvertising()
        }
        haTransmitter.transmitting = false
        haTransmitter.state = "Stopped"
    }
}
