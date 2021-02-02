package io.homeassistant.companion.android.bluetooth.ble

import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseSettings
import android.content.Context
import org.altbeacon.beacon.Beacon
import org.altbeacon.beacon.BeaconParser
import org.altbeacon.beacon.BeaconTransmitter
object TransmitterManager {
    private lateinit var physicalTransmitter: BeaconTransmitter
    private lateinit var beacon: Beacon

    private fun buildBeacon(haTransmitter: BLETransmitter): Beacon {
        val builder = Beacon.Builder()
        builder.setTxPower(-59)
        builder.setId1(haTransmitter.id1)
        builder.setId2(haTransmitter.id2)
        builder.setId3(haTransmitter.id3)
        builder.setManufacturer(0x004c) //  apple ibeacon
        beacon = builder.build()
        return beacon
    }

    private fun idsHaveChanged(haTransmitter: BLETransmitter): Boolean {
        return this::beacon.isInitialized && (haTransmitter.id1.toString() != beacon.id1.toString() || haTransmitter.id2 != beacon.id2.toString() || haTransmitter.id3 != beacon.id3.toString())
    }

    private fun shouldStartTransmitting(haTransmitter: BLETransmitter): Boolean {
        return (!this::physicalTransmitter.isInitialized || !physicalTransmitter.isStarted || !idsHaveChanged(haTransmitter))
    }

    @Synchronized
    fun startTransmitting(context: Context, haTransmitter: BLETransmitter) {
        if (!shouldStartTransmitting(haTransmitter)) {
            return
        }
        if (idsHaveChanged(haTransmitter) && this::physicalTransmitter.isInitialized) {
            stopTransmitting(haTransmitter)
        }
        if (!this::physicalTransmitter.isInitialized) {
            val parser = BeaconParser().setBeaconLayout("m:2-3=0215,i:4-19,i:20-21,i:22-23,p:24-24") // ibeacon only
            physicalTransmitter = BeaconTransmitter(context, parser)
            physicalTransmitter.advertiseMode = AdvertiseSettings.ADVERTISE_MODE_LOW_POWER
            physicalTransmitter.advertiseTxPowerLevel = AdvertiseSettings.ADVERTISE_TX_POWER_HIGH
        }
        val bluetoothOn = BluetoothAdapter.getDefaultAdapter().isEnabled
        if (bluetoothOn) {
            val beacon = buildBeacon(haTransmitter)

            physicalTransmitter.startAdvertising(beacon, object : AdvertiseCallback() {
                override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
                    haTransmitter.transmitting = true
                    haTransmitter.state = "Transmitting"
                }

                override fun onStartFailure(errorCode: Int) {
                    if (errorCode != ADVERTISE_FAILED_ALREADY_STARTED) {
                        haTransmitter.id1 = ""
                        haTransmitter.id2 = ""
                        haTransmitter.id3 = ""
                        haTransmitter.state = "Unable to transmit"
                        haTransmitter.transmitting = false
                    } else {
                        haTransmitter.transmitting = true
                        haTransmitter.state = "Transmitting"
                    }
                }
            })
        }
    }

    fun stopTransmitting(haTransmitter: BLETransmitter) {
        if (haTransmitter.transmitting && this::physicalTransmitter.isInitialized) {
            if (physicalTransmitter.isStarted)
                physicalTransmitter.stopAdvertising()
        }
        haTransmitter.transmitting = false
        haTransmitter.state = "Stopped"
    }
}
