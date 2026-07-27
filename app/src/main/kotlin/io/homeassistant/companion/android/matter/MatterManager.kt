package io.homeassistant.companion.android.matter

import android.content.IntentSender
import androidx.activity.result.ActivityResult
import io.homeassistant.companion.android.common.data.websocket.impl.entities.MatterCommissionResponse

interface MatterManager {

    /**
     * Terminal result of [MatterManager.prepareMatterDeviceCommissioning].
     *
     * Callers launch [Ready.intentSender] to continue the Matter commissioning
     * flow, or display an error message derived from [Error.cause].
     */
    sealed interface CommissioningResult {

        /**
         * The request produced an [IntentSender] for the commissioning flow. Callers must launch it
         * from an Activity (typically via an `ActivityResultLauncher`) to continue.
         */
        data class Ready(val intentSender: IntentSender) : CommissioningResult

        /**
         * The manager could not prepare the commissioning flow — common causes are an unsupported
         * device (SDK < O_MR1 or Automotive), Play Services unavailable, or a network failure
         * resolving the request. The caller should surface a user-facing error.
         */
        data class Error(val cause: Throwable) : CommissioningResult
    }

    /**
     * Terminal outcome of the platform commissioning flow launched from
     * [CommissioningResult.Ready.intentSender], derived from its `ActivityResult` by
     * [parseCommissioningIntentResult].
     */
    sealed interface CommissioningFlowOutcome {

        /**
         * The device was commissioned. [deviceName] is the name the user entered during the
         * platform flow, or `null` when none was provided.
         */
        data class Success(val deviceName: String?) : CommissioningFlowOutcome

        /** The flow was cancelled by the user or failed before the device was commissioned. */
        data object Failed : CommissioningFlowOutcome
    }

    /**
     * Indicates if the app on this device supports Matter commissioning.
     */
    fun appSupportsCommissioning(): Boolean

    /**
     * Interpret the `ActivityResult` of the commissioning flow launched from
     * [CommissioningResult.Ready.intentSender].
     */
    fun parseCommissioningIntentResult(result: ActivityResult): CommissioningFlowOutcome

    /**
     * Indicates if the server supports Matter commissioning.
     */
    suspend fun coreSupportsCommissioning(serverId: Int): Boolean

    /**
     * Prevent the bottom sheet for discovered Matter devices from showing up while the app is open.
     */
    fun suppressDiscoveryBottomSheet()

    /**
     * Prepare a Matter device commissioning session.
     *
     * Returns exactly one [CommissioningResult]:
     *   - [CommissioningResult.Ready] when Play Services produced an `IntentSender` the caller
     *     must launch from an Activity to continue the flow.
     *   - [CommissioningResult.Error] when commissioning is unsupported on this device
     *     (SDK < O_MR1, Automotive, minimal flavor) or Play Services failed to prepare the flow.
     */
    suspend fun prepareMatterDeviceCommissioning(): CommissioningResult

    /**
     * Send a request to the server to add a Matter device to the network and commission it.
     * @return [MatterCommissionResponse], or `null` if it wasn't possible to complete the request.
     */
    suspend fun commissionDevice(code: String, serverId: Int): MatterCommissionResponse?

    /**
     * Send a request to the server to commission an "on network" Matter device.
     * @return [MatterCommissionResponse], or `null` if it wasn't possible to complete the request.
     */
    suspend fun commissionOnNetworkDevice(pin: Long, ip: String, serverId: Int): MatterCommissionResponse?
}
