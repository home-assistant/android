package io.homeassistant.companion.android.common.bluetooth.ble

import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseSettings
import android.content.Context
import androidx.core.content.getSystemService
import io.homeassistant.companion.android.common.sensors.BluetoothSensorManager
import java.util.UUID
import org.altbeacon.beacon.Beacon
import org.altbeacon.beacon.BeaconManager
import org.altbeacon.beacon.BeaconParser
import org.altbeacon.beacon.BeaconTransmitter
import org.altbeacon.beacon.Identifier
import org.altbeacon.beacon.Region

object TransmitterManager {
    private lateinit var physicalTransmitter: BeaconTransmitter
    private var beaconManager: BeaconManager? = null
    private lateinit var beacon: Beacon
    private val region = Region("dummy-region", Identifier.parse("FFFFFFFF-FFFF-FFFF-FFFF-FFFFFFFFFFFF"), null, null)

    private fun buildBeacon(haTransmitterI: IBeaconTransmitter): Beacon {
        val builder = Beacon.Builder()
        builder.setTxPower(haTransmitterI.measuredPowerSetting)
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
            if (haTransmitter.major.toInt() < 0 || haTransmitter.major.toInt() > 65535 || haTransmitter.minor.toInt() < 0 || haTransmitter.minor.toInt() > 65535 || haTransmitter.measuredPowerSetting >= 0) {
                throw IllegalArgumentException("Invalid Major or Minor")
            }
        } catch (e: IllegalArgumentException) {
            stopTransmitting(haTransmitter)
            haTransmitter.state = "Invalid parameters, check UUID, Major and Minor, and Measured Power settings."
            return false
        }
        return true
    }

    @Synchronized
    fun startTransmitting(context: Context, haTransmitter: IBeaconTransmitter) {
        if (!shouldStartTransmitting(haTransmitter)) {
            return
        }
        if (haTransmitter.restartRequired && this::physicalTransmitter.isInitialized) {
            stopTransmitting(haTransmitter)
        }
        if (!this::physicalTransmitter.isInitialized) {
            val parser = BeaconParser().setBeaconLayout(haTransmitter.beaconLayout)
            physicalTransmitter = BeaconTransmitter(context, parser)
        }
        val bluetoothAdapter = context.getSystemService<BluetoothManager>()?.adapter
        val bluetoothOn = bluetoothAdapter?.isEnabled == true
        beaconManager = BeaconManager.getInstanceForApplication(context)

        if (bluetoothOn) {
            val beacon = buildBeacon(haTransmitter)
            if (!physicalTransmitter.isStarted) {
                val builder = beaconNotification(true, context)
                beaconManager?.enableForegroundServiceScanning(builder.build(), 445)
                beaconManager?.setEnableScheduledScanJobs(false)
                beaconManager?.beaconParsers?.clear()
                beaconManager?.backgroundBetweenScanPeriod = Long.MAX_VALUE
                beaconManager?.backgroundScanPeriod = 0
                beaconManager?.foregroundBetweenScanPeriod = Long.MAX_VALUE
                beaconManager?.foregroundScanPeriod = 0
                beaconManager?.startMonitoring(region)
                physicalTransmitter.advertiseTxPowerLevel = getPowerLevel(haTransmitter)
                physicalTransmitter.advertiseMode = getAdvertiseMode(haTransmitter)
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

    private fun getAdvertiseMode(haTransmitter: IBeaconTransmitter) =
        when (haTransmitter.advertiseModeSetting) {
            BluetoothSensorManager.BLE_ADVERTISE_LOW_LATENCY -> AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY
            BluetoothSensorManager.BLE_ADVERTISE_BALANCED -> AdvertiseSettings.ADVERTISE_MODE_BALANCED
            BluetoothSensorManager.BLE_ADVERTISE_LOW_POWER -> AdvertiseSettings.ADVERTISE_MODE_LOW_POWER // explicit for code readability
            else -> AdvertiseSettings.ADVERTISE_MODE_LOW_POWER
        }

    private fun getPowerLevel(haTransmitter: IBeaconTransmitter) =
        when (haTransmitter.transmitPowerSetting) {
            BluetoothSensorManager.BLE_TRANSMIT_HIGH -> AdvertiseSettings.ADVERTISE_TX_POWER_HIGH
            BluetoothSensorManager.BLE_TRANSMIT_MEDIUM -> AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM
            BluetoothSensorManager.BLE_TRANSMIT_LOW -> AdvertiseSettings.ADVERTISE_TX_POWER_LOW
            else -> AdvertiseSettings.ADVERTISE_TX_POWER_ULTRA_LOW
        }

    fun stopTransmitting(haTransmitter: IBeaconTransmitter) {
        if (haTransmitter.transmitting && this::physicalTransmitter.isInitialized) {
            if (physicalTransmitter.isStarted) {
                physicalTransmitter.stopAdvertising()
            }
        }
        haTransmitter.transmitting = false
        haTransmitter.state = "Stopped"
        beaconManager?.stopMonitoring(region)
        beaconManager?.disableForegroundServiceScanning()
    }
}
