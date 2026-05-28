package io.homeassistant.companion.android.matter

import io.homeassistant.companion.android.common.data.websocket.impl.entities.MatterCommissionResponse

interface MatterManager {

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
     * Prepare a Matter device commissioning session via Google Play Services.
     *
     * Returns exactly one [MatterCommissioningResult]:
     *   - [MatterCommissioningResult.Ready] when Play Services produced an `IntentSender` the caller
     *     must launch from an Activity to continue the flow.
     *   - [MatterCommissioningResult.Error] when commissioning is unsupported on this device
     *     (SDK < O_MR1, Automotive, minimal flavor) or Play Services failed to prepare the flow.
     */
    suspend fun commissionMatterDevice(): MatterCommissioningResult

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
