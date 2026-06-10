package io.homeassistant.companion.android.frontend.improv

import com.wifi.improv.DeviceState
import com.wifi.improv.ErrorState

/**
 * Events emitted during a single Improv Wi-Fi device provisioning session.
 *
 * Produced by [ImprovRepository.provisionDevice] across the connect → authorize → submit-Wi-Fi →
 * provision sequence. The flow terminates with a [Provisioned] emission once the device reports
 * successful onboarding, or ends when the collector cancels.
 */
sealed interface ProvisioningEvent {

    /**
     * The device transitioned to a new [DeviceState]. Transitions usually run
     * `AUTHORIZATION_REQUIRED` → `AUTHORIZED` → `PROVISIONING` → `PROVISIONED`, but the device
     * dictates the actual order — collectors should treat the state machine as advisory and not
     * gate logic on a specific transition having already happened.
     */
    data class StateChanged(val state: DeviceState) : ProvisioningEvent

    /**
     * The device reported an [ErrorState]. **Not terminal** — the flow keeps emitting events
     * afterwards, so the collector decides whether to keep waiting (e.g. for a recoverable
     * timeout) or cancel its subscription to abort the session.
     */
    data class ErrorOccurred(val error: ErrorState) : ProvisioningEvent

    /**
     * Terminal event: the device finished onboarding and the flow completes after this
     * emission. [domain] is the Home Assistant integration domain parsed out of the device's
     * RPC result (a `config_flow_start` URL — e.g. `"esphome"`), or `null` when the device
     * didn't advertise one.
     */
    data class Provisioned(val domain: String?) : ProvisioningEvent
}
