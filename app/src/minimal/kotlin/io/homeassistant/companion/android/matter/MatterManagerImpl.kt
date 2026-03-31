package io.homeassistant.companion.android.matter

import android.content.Context
import android.content.IntentSender
import io.homeassistant.companion.android.common.data.websocket.impl.entities.MatterCommissionResponse
import javax.inject.Inject

class MatterManagerImpl @Inject constructor() : MatterManager {

    // Matter support currently depends on Google Play Services,
    // and as a result Matter is not supported with the minimal flavor

    override fun appSupportsCommissioning(): Boolean = false

    override suspend fun coreSupportsCommissioning(serverId: Int): Boolean = false

    override fun suppressDiscoveryBottomSheet(context: Context) {
        // No support, so nothing to suppress
    }

    override fun startNewCommissioningFlow(
        context: Context,
        onSuccess: (IntentSender) -> Unit,
        onFailure: (Exception) -> Unit,
    ) {
        onFailure(IllegalStateException("Matter commissioning is not supported with the minimal flavor"))
    }

    override suspend fun commissionDevice(code: String, serverId: Int): MatterCommissionResponse? = null

    override suspend fun commissionOnNetworkDevice(pin: Long, ip: String, serverId: Int): MatterCommissionResponse? =
        null
}
