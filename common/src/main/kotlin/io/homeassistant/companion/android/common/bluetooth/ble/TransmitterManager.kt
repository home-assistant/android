package io.homeassistant.companion.android.common.bluetooth.ble

import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseSettings
import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.core.content.getSystemService
import io.homeassistant.companion.android.common.sensors.BluetoothSensorManager
import java.util.UUID
import org.altbeacon.beacon.Beacon
import org.altbeacon.beacon.BeaconParser
import org.altbeacon.beacon.BeaconTransmitter
import timber.log.Timber

object TransmitterManager {
    private var physicalTransmitter: BeaconTransmitter? = null
    private var startPending: Boolean = false
    private lateinit var beacon: Beacon

    private val defaultTransmitterFactory: (Context, String) -> BeaconTransmitter = { context, layout ->
        BeaconTransmitter(context, BeaconParser().setBeaconLayout(layout))
    }

    /**
     * Creates the [BeaconTransmitter] used for advertising. Swappable in tests to observe and
     * control advertising without the Bluetooth stack.
     */
    @VisibleForTesting
    internal var transmitterFactory: (Context, String) -> BeaconTransmitter = defaultTransmitterFactory

    /**
     * Drops the current transmitter and restores the default factory. Only for tests: the manager
     * is a process-wide singleton, so tests must not leak transmitter state into each other.
     */
    @VisibleForTesting
    internal fun resetForTesting() {
        physicalTransmitter = null
        startPending = false
        transmitterFactory = defaultTransmitterFactory
    }

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
        return !startPending &&
            validateInputs(haTransmitter) &&
            (physicalTransmitter?.isStarted != true || haTransmitter.restartRequired)
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

    /**
     * Starts BLE advertising with the settings from [haTransmitter]. When a restart is required
     * the current advertisement is stopped first, so a stale advertisement left behind by the
     * Bluetooth stack cannot block the new one.
     */
    @Synchronized
    fun startTransmitting(context: Context, haTransmitter: IBeaconTransmitter) {
        if (!shouldStartTransmitting(haTransmitter)) {
            return
        }
        val transmitter = ensureTransmitter(context, haTransmitter)
        if (haTransmitter.restartRequired) {
            stopTransmitting(haTransmitter)
        }
        val bluetoothAdapter = context.getSystemService<BluetoothManager>()?.adapter
        val bluetoothOn = bluetoothAdapter?.isEnabled == true
        if (bluetoothOn) {
            val beacon = buildBeacon(haTransmitter)
            if (!transmitter.isStarted) {
                startAdvertising(context, haTransmitter, beacon, retryOnAlreadyStarted = true)
            }
        } else {
            stopTransmitting(haTransmitter)
        }
    }

    private fun ensureTransmitter(context: Context, haTransmitter: IBeaconTransmitter): BeaconTransmitter {
        return physicalTransmitter ?: transmitterFactory(context, haTransmitter.beaconLayout).also {
            physicalTransmitter = it
        }
    }

    /**
     * Starts advertising on the current transmitter. A start rejected as already started means
     * the Bluetooth stack still holds a stale advertisement, so it is retried once with a fresh
     * transmitter, which registers a new callback with the stack. A failed start is never
     * reported as transmitting, so the next sensor update retries instead of staying wedged.
     *
     * The start is asynchronous: the callback fires later, so [startPending] guards the window
     * between requesting the start and hearing back. Without it, a second sensor update in that
     * window would start a second advertiser, and the retry path would replace the transmitter
     * without stopping the first one, orphaning it (nothing would ever stop it).
     */
    @Synchronized
    private fun startAdvertising(
        context: Context,
        haTransmitter: IBeaconTransmitter,
        beacon: Beacon,
        retryOnAlreadyStarted: Boolean,
    ) {
        val transmitter = physicalTransmitter ?: return
        transmitter.advertiseTxPowerLevel = getPowerLevel(haTransmitter)
        transmitter.advertiseMode = getAdvertiseMode(haTransmitter)
        startPending = true
        transmitter.startAdvertising(
            beacon,
            object : AdvertiseCallback() {
                override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
                    onStartResult(context, haTransmitter, beacon, errorCode = null, retryOnAlreadyStarted)
                }

                override fun onStartFailure(errorCode: Int) {
                    onStartResult(context, haTransmitter, beacon, errorCode, retryOnAlreadyStarted)
                }
            },
        )
    }

    @Synchronized
    private fun onStartResult(
        context: Context,
        haTransmitter: IBeaconTransmitter,
        beacon: Beacon,
        errorCode: Int?,
        retryOnAlreadyStarted: Boolean,
    ) {
        if (errorCode == null) {
            startPending = false
            Timber.d("BLE advertisement started")
            haTransmitter.transmitting = true
            haTransmitter.state = "Transmitting"
            return
        }
        if (errorCode == AdvertiseCallback.ADVERTISE_FAILED_ALREADY_STARTED && retryOnAlreadyStarted) {
            Timber.d("Advertisement already started, retrying with a fresh transmitter")
            // Stop the original before replacing it: it may already be advertising, and
            // replacing it without stopping would orphan it. Stopping a transmitter that
            // never started is a no-op.
            physicalTransmitter?.stopAdvertising()
            physicalTransmitter = transmitterFactory(context, haTransmitter.beaconLayout)
            startAdvertising(context, haTransmitter, beacon, retryOnAlreadyStarted = false)
            return
        }
        startPending = false
        Timber.w("BLE advertisement failed to start, error code: $errorCode")
        if (errorCode != AdvertiseCallback.ADVERTISE_FAILED_ALREADY_STARTED) {
            haTransmitter.uuid = ""
            haTransmitter.major = ""
            haTransmitter.minor = ""
        }
        haTransmitter.state = "Unable to transmit"
        haTransmitter.transmitting = false
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

    /**
     * Stops BLE advertising, if running. Always clears the transmitting state, so a later sensor
     * update can start advertising again.
     */
    fun stopTransmitting(haTransmitter: IBeaconTransmitter) {
        if (haTransmitter.transmitting) {
            physicalTransmitter?.takeIf { it.isStarted }?.stopAdvertising()
        }
        haTransmitter.transmitting = false
        haTransmitter.state = "Stopped"
    }
}
