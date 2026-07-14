package io.homeassistant.companion.android.frontend.improv

import com.wifi.improv.DeviceState
import com.wifi.improv.ErrorState

/**
 * UI state of the Improv Wi-Fi onboarding flow, modelled as a state machine:
 *
 * [SearchingDevice] → [ConfiguringDevice] → [Provisioning] → ([Provisioned] | [Errored])
 *
 * [Errored] folds back to [ConfiguringDevice] on a retry — that's why the variants from
 * [ConfiguringDevice] onward implement [WithResolvedDevice] and carry both the device name and
 * the resolved BLE address, so the revert is a no-lookup state copy.
 *
 * The flow only ever opens for a known device, so there is no device-list picker variant:
 * [SearchingDevice] carries the target name up front and is promoted to [ConfiguringDevice] as
 * soon as the BLE address is resolved.
 */
sealed interface ImprovUIState {

    /** Variants that know the target [deviceName]. */
    sealed interface WithDeviceName : ImprovUIState {
        val deviceName: String
    }

    /**
     * Variants that have both the [deviceName] and its resolved BLE [deviceAddress] — any of
     * these can revert to [ConfiguringDevice] via a one-line state copy on retry.
     */
    sealed interface WithResolvedDevice : WithDeviceName {
        val deviceAddress: String
    }

    /** The BLE scan hasn't surfaced a device matching [deviceName] yet. */
    data class SearchingDevice(override val deviceName: String) : WithDeviceName

    /**
     * The target device has been located over BLE — [deviceAddress] is known and Wi-Fi
     * credentials can be submitted.
     *
     * @param activeSsid Currently-connected Wi-Fi SSID, captured so it can be pre-filled into
     *   the credentials input.
     */
    data class ConfiguringDevice(
        override val deviceName: String,
        override val deviceAddress: String,
        val activeSsid: String? = null,
    ) : WithResolvedDevice

    /**
     * BLE handshake in progress: connect → authorize → submit-Wi-Fi → provisioned. [state] is
     * `null` after the credentials are submitted and before the first device-state update
     * arrives.
     */
    data class Provisioning(
        override val deviceName: String,
        override val deviceAddress: String,
        val state: DeviceState? = null,
    ) : WithResolvedDevice

    /**
     * Provisioning failed. A retry transitions back to [ConfiguringDevice] for the same
     * [deviceName]/[deviceAddress].
     */
    data class Errored(override val deviceName: String, override val deviceAddress: String, val error: ErrorState) :
        WithResolvedDevice

    /**
     * Terminal success: the device reported [DeviceState.PROVISIONED]. [domain] is the Home
     * Assistant integration `domain` parsed out of the device's RPC result — `null` when the
     * device didn't report a follow-up config flow.
     */
    data class Provisioned(val domain: String?) : ImprovUIState
}
