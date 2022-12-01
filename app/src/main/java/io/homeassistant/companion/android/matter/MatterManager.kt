package io.homeassistant.companion.android.matter

import android.content.Context
import android.content.IntentSender

interface MatterManager {

    /**
     * Indicates if the app on this device supports Matter commissioning
     */
    fun appSupportsCommissioning(): Boolean

    /**
     * Indicates if the server supports Matter commissioning
     */
    suspend fun coreSupportsCommissioning(): Boolean

    /**
     * Start a flow to commission a Matter device that is on the same network as this device.
     * @param onSuccess Callback that receives an intent to launch the commissioning flow
     * @param onFailure Callback for an exception if the commissioning flow cannot be started
     */
    fun startNewCommissioningFlow(
        context: Context,
        onSuccess: (IntentSender) -> Unit,
        onFailure: (Exception) -> Unit
    )

    /**
     * Send a request to the server to commission an "on network" Matter device
     * @return `true` if the request was successful
     */
    suspend fun commissionOnNetworkDevice(pin: String): Boolean
}
