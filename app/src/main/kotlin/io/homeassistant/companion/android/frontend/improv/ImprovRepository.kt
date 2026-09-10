package io.homeassistant.companion.android.frontend.improv

import com.wifi.improv.ImprovDevice
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for the [Improv Wi-Fi onboarding protocol](https://www.improv-wifi.com).
 *
 * Exposes the protocol as two Flow operations, each fully driven by the collector's lifecycle.
 *
 * Both operations require the Android runtime permissions reported by [requiredPermissions];
 * callers are responsible for requesting them before subscribing.
 */
interface ImprovRepository {

    /**
     * Android runtime permissions required for the BLE scan and the per-device GATT handshake.
     * The set varies by platform SDK.
     */
    val requiredPermissions: List<String>

    /** Whether every entry in [requiredPermissions] are currently granted to the app. */
    fun hasPermissions(): Boolean

    /**
     * Emits the cumulative list of devices discovered by the active BLE scan.
     *
     * Hot: subscribing starts the scan (or joins one already in flight); the scan tears down
     * shortly after the last subscriber detaches.
     */
    fun scanDevices(): Flow<List<ImprovDevice>>

    /**
     * Runs the BLE provisioning handshake against [device]:
     *
     * 1. Open a GATT connection.
     * 2. Wait for the device to report it's authorized — some hardware requires a physical
     *    button press here.
     * 3. Send [ssid] / [password] over the Improv characteristic.
     * 4. Forward the device's state machine until it reports it has been provisioned.
     *
     * Emits a [ProvisioningEvent] for every state transition, error report, and the terminal
     * [ProvisioningEvent.Provisioned] carrying the integration `domain` the device advertises
     * (e.g. `"esphome"`) — or `null` when none was reported. The flow completes normally after
     * that terminal event; cancelling the collector earlier aborts the session.
     *
     * Credentials are confined to this call's parameters the repository does not retain them.
     */
    fun provisionDevice(device: ImprovDevice, ssid: String, password: String): Flow<ProvisioningEvent>
}
