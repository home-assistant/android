package io.homeassistant.companion.android.matter

import android.content.IntentSender
import androidx.activity.result.ActivityResult
import io.homeassistant.companion.android.common.data.websocket.impl.entities.MatterCommissionResponse

interface MatterManager {

    /**
     * Terminal result of [MatterManager.prepareMatterDeviceCommissioning] and
     * [MatterManager.prepareDeviceSharing].
     *
     * Callers launch [Ready.intentSender] to continue the flow, or report an error derived from
     * [Error.cause].
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
     * Terminal outcome of the platform commissioning request launched from
     * [CommissioningResult.Ready.intentSender], derived from its `ActivityResult` by
     * [parseCommissioningIntentResult].
     */
    sealed interface CommissioningRequestResult {

        /**
         * The device was commissioned. [deviceName] is the name the user entered during the
         * platform request, or `null` when none was provided.
         */
        data class Success(val deviceName: String?) : CommissioningRequestResult

        /** The request was cancelled by the user or failed before the device was commissioned. */
        data object Failed : CommissioningRequestResult
    }

    /**
     * Terminal outcome of the platform share request launched from the
     * [CommissioningResult.Ready.intentSender] returned by [prepareDeviceSharing], derived from its
     * `ActivityResult` by [parseSharingIntentResult].
     */
    sealed interface SharingRequestResult {
        /**
         * The platform finished its share flow. The user either added the device to an app, or took
         * a pairing code from the sheet to enter elsewhere.
         */
        data object Shared : SharingRequestResult

        /** The user backed out of the platform sheet. */
        data object Cancelled : SharingRequestResult

        /** The platform could not add the device, for example because the commissioning window closed. */
        data object Failed : SharingRequestResult
    }

    /**
     * Indicates if the app on this device supports Matter commissioning.
     */
    fun appSupportsCommissioning(): Boolean

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

    /**
     * Interpret the `ActivityResult` of the commissioning flow launched from
     * [CommissioningResult.Ready.intentSender].
     */
    fun parseCommissioningIntentResult(result: ActivityResult): CommissioningRequestResult

    /**
     * Indicates if the app on this device can share a device already commissioned to Home Assistant
     * with another app through the platform share sheet (Matter multi-admin).
     */
    fun appSupportsSharing(): Boolean

    /**
     * Prepare sharing a device already commissioned to Home Assistant with the platform's home app,
     * through the commissioning window Home Assistant opened for it.
     *
     * Returns [CommissioningResult.Ready] with the `IntentSender` of the platform share sheet the
     * caller must launch from an Activity, or [CommissioningResult.Error] when sharing is unsupported
     * or Play Services failed.
     *
     * @param request the open commissioning window to share
     */
    suspend fun prepareDeviceSharing(request: MatterShareRequest): CommissioningResult

    /**
     * Interpret the `ActivityResult` of the share flow launched from the
     * [CommissioningResult.Ready.intentSender] returned by [prepareDeviceSharing].
     */
    fun parseSharingIntentResult(result: ActivityResult): SharingRequestResult
}
