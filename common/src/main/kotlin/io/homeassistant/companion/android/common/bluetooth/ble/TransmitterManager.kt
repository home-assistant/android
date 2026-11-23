package io.homeassistant.companion.android.common.bluetooth.ble

import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseSettings
import android.content.Context
import androidx.core.content.getSystemService
import io.homeassistant.companion.android.common.sensors.BluetoothSensorManager
import java.util.UUID
import kotlinx.coroutines.suspendCancellableCoroutine
import org.altbeacon.beacon.Beacon
import org.altbeacon.beacon.BeaconParser
import org.altbeacon.beacon.BeaconTransmitter

object TransmitterManager {
    private lateinit var physicalTransmitter: BeaconTransmitter
    private lateinit var beacon: Beacon

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
        return validateInputs(haTransmitter) &&
            (
                !this::physicalTransmitter.isInitialized ||
                    !physicalTransmitter.isStarted ||
                    haTransmitter.restartRequired
                )
    }

    private fun validateInputs(haTransmitter: IBeaconTransmitter): Boolean {
        try {
            UUID.fromString(haTransmitter.uuid)
            if (haTransmitter.major.toInt() < 0 ||
                haTransmitter.major.toInt() > 65535 ||
                haTransmitter.minor.toInt() < 0 ||
                haTransmitter.minor.toInt() > 65535 ||
                haTransmitter.measuredPowerSetting >= 0
            ) {
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
        startTransmitting(context, haTransmitter, onResult = null)
    }

    /**
     * Starts transmitting and suspends until the advertising callback reports success or failure.
     *
     * Returns true if advertising started (or was already started), false otherwise. When the
     * transmitter is already started and no restart is required, or Bluetooth is off, there is
     * no new advertising request to wait for and this returns immediately. The Android BLE API
     * guarantees the [AdvertiseCallback] is invoked once per [BeaconTransmitter.startAdvertising]
     * call, so the suspension always completes.
     */
    suspend fun startTransmittingAndAwait(context: Context, haTransmitter: IBeaconTransmitter): Boolean {
        return suspendCancellableCoroutine { continuation ->
            val submitted = startTransmitting(context, haTransmitter) { success ->
                if (continuation.isActive) {
                    continuation.resume(success, null)
                }
            }
            if (!submitted && continuation.isActive) {
                // Already transmitting or Bluetooth off: no callback will fire.
                continuation.resume(haTransmitter.transmitting, null)
            }
        }
    }

    /**
     * Starts transmitting. Returns true when a new advertising request was submitted (so
     * [onResult] will be invoked with the outcome), false when nothing was submitted because
     * the transmitter is already started, Bluetooth is off, or the inputs are invalid.
     */
    @Synchronized
    private fun startTransmitting(
        context: Context,
        haTransmitter: IBeaconTransmitter,
        onResult: ((Boolean) -> Unit)?,
    ): Boolean {
        if (!shouldStartTransmitting(haTransmitter)) {
            return false
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
        return if (bluetoothOn && !physicalTransmitter.isStarted) {
            val beacon = buildBeacon(haTransmitter)
            physicalTransmitter.advertiseTxPowerLevel = getPowerLevel(haTransmitter)
            physicalTransmitter.advertiseMode = getAdvertiseMode(haTransmitter)
            physicalTransmitter.startAdvertising(
                beacon,
                object : AdvertiseCallback() {
                    override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
                        haTransmitter.transmitting = true
                        haTransmitter.state = "Transmitting"
                        onResult?.invoke(true)
                    }

                    override fun onStartFailure(errorCode: Int) {
                        if (errorCode != ADVERTISE_FAILED_ALREADY_STARTED) {
                            haTransmitter.uuid = ""
                            haTransmitter.major = ""
                            haTransmitter.minor = ""
                            haTransmitter.state = "Unable to transmit"
                            haTransmitter.transmitting = false
                            onResult?.invoke(false)
                        } else {
                            haTransmitter.transmitting = true
                            haTransmitter.state = "Transmitting"
                            onResult?.invoke(true)
                        }
                    }
                },
            )
            true
        } else {
            if (!bluetoothOn) {
                stopTransmitting(haTransmitter)
            }
            false
        }
    }

    private fun getAdvertiseMode(haTransmitter: IBeaconTransmitter) = when (haTransmitter.advertiseModeSetting) {
        BluetoothSensorManager.BLE_ADVERTISE_LOW_LATENCY -> AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY
        BluetoothSensorManager.BLE_ADVERTISE_BALANCED -> AdvertiseSettings.ADVERTISE_MODE_BALANCED
        // explicit for code readability
        BluetoothSensorManager.BLE_ADVERTISE_LOW_POWER -> AdvertiseSettings.ADVERTISE_MODE_LOW_POWER
        else -> AdvertiseSettings.ADVERTISE_MODE_LOW_POWER
    }

    private fun getPowerLevel(haTransmitter: IBeaconTransmitter) = when (haTransmitter.transmitPowerSetting) {
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
    }
}
