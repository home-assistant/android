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

    private fun buildBeacon(haTransmitter: IBeaconTransmitter): Beacon {
        val builder = Beacon.Builder()
        builder.setTxPower(-59)
        builder.setId1(haTransmitter.uuid)
        builder.setId2(haTransmitter.major)
        builder.setId3(haTransmitter.minor)
        builder.setManufacturer(haTransmitter.manufacturer)
        beacon = builder.build()
        return beacon
    }

    private fun idsHaveChanged(haTransmitter: IBeaconTransmitter): Boolean {
        return this::beacon.isInitialized && (haTransmitter.uuid.toString() != beacon.id1.toString() || haTransmitter.major != beacon.id2.toString() || haTransmitter.minor != beacon.id3.toString())
    }

    private fun shouldStartTransmitting(haTransmitter: IBeaconTransmitter): Boolean {
        return (!this::physicalTransmitter.isInitialized || !physicalTransmitter.isStarted || !idsHaveChanged(haTransmitter))
    }

    @Synchronized
    fun startTransmitting(context: Context, haTransmitter: IBeaconTransmitter) {
        if (!shouldStartTransmitting(haTransmitter)) {
            return
        }
        if (idsHaveChanged(haTransmitter) && this::physicalTransmitter.isInitialized) {
            stopTransmitting(haTransmitter)
        }
        if (!this::physicalTransmitter.isInitialized) {
            val parser = BeaconParser().setBeaconLayout(haTransmitter.beaconLayout)
            physicalTransmitter = BeaconTransmitter(context, parser)
            physicalTransmitter.advertiseMode = AdvertiseSettings.ADVERTISE_MODE_LOW_POWER
            physicalTransmitter.advertiseTxPowerLevel = AdvertiseSettings.ADVERTISE_TX_POWER_HIGH
        }
        val bluetoothOn = BluetoothAdapter.getDefaultAdapter().isEnabled
        if (bluetoothOn) {
            val beacon = buildBeacon(haTransmitter)
            if (!physicalTransmitter.isStarted) {
                physicalTransmitter.startAdvertising(beacon, object : AdvertiseCallback() {
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
                })
            }
        } else {
            stopTransmitting(haTransmitter)
        }
    }

    fun stopTransmitting(haTransmitter: IBeaconTransmitter) {
        if (haTransmitter.transmitting && this::physicalTransmitter.isInitialized) {
            if (physicalTransmitter.isStarted)
                physicalTransmitter.stopAdvertising()
        }
        haTransmitter.transmitting = false
        haTransmitter.state = "Stopped"
    }
}
