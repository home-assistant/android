package io.homeassistant.companion.android.matter

import android.content.Context
import android.content.IntentSender
import io.homeassistant.companion.android.common.data.websocket.impl.entities.MatterCommissionResponse

interface MatterManager {

    /**
     * Indicates if the app on this device supports Matter commissioning
     */
    fun appSupportsCommissioning(): Boolean

    /**
     * Indicates if the server supports Matter commissioning
     */
    suspend fun coreSupportsCommissioning(serverId: Int): Boolean

    /**
     * Prevent the bottom sheet for discovered Matter devices from showing up while the app is open.
     */
    fun suppressDiscoveryBottomSheet(context: Context)

    /**
     * Start a flow to commission a Matter device that is on the same network as this device.
     * @param onSuccess Callback that receives an intent to launch the commissioning flow
     * @param onFailure Callback for an exception if the commissioning flow cannot be started
     */
    fun startNewCommissioningFlow(context: Context, onSuccess: (IntentSender) -> Unit, onFailure: (Exception) -> Unit)

    /**
     * Send a request to the server to add a Matter device to the network and commission it
     * @return [MatterCommissionResponse], or `null` if it wasn't possible to complete the request
     */
    suspend fun commissionDevice(code: String, serverId: Int): MatterCommissionResponse?

    /**
     * Send a request to the server to commission an "on network" Matter device
     * @return [MatterCommissionResponse], or `null` if it wasn't possible to complete the request
     */
    suspend fun commissionOnNetworkDevice(pin: Long, ip: String, serverId: Int): MatterCommissionResponse?
}
