package io.homeassistant.companion.android.matter

import android.content.ComponentName
import android.content.Context
import android.content.IntentSender
import android.os.Build
import android.util.Log
import com.google.android.gms.home.matter.Matter
import com.google.android.gms.home.matter.commissioning.CommissioningRequest
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.data.websocket.impl.entities.MatterCommissionResponse
import javax.inject.Inject

class MatterManagerImpl @Inject constructor(
    private val serverManager: ServerManager
) : MatterManager {

    companion object {
        private const val TAG = "MatterManagerImpl"
    }

    override fun appSupportsCommissioning(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1

    override suspend fun coreSupportsCommissioning(): Boolean {
        if (!serverManager.isRegistered()) return false
        val config = serverManager.webSocketRepository().getConfig()
        return config != null && config.components.contains("matter")
    }

    override fun startNewCommissioningFlow(
        context: Context,
        onSuccess: (IntentSender) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        if (appSupportsCommissioning()) {
            Matter.getCommissioningClient(context)
                .commissionDevice(
                    CommissioningRequest.builder()
                        .setCommissioningService(ComponentName(context, MatterCommissioningService::class.java))
                        .build()
                )
                .addOnSuccessListener { onSuccess(it) }
                .addOnFailureListener { onFailure(it) }
        } else {
            onFailure(IllegalStateException("Matter commissioning is not supported on SDK <27"))
        }
    }

    override suspend fun commissionDevice(code: String): MatterCommissionResponse? {
        return try {
            serverManager.webSocketRepository().commissionMatterDevice(code)
        } catch (e: Exception) {
            Log.e(TAG, "Error while executing server commissioning request", e)
            null
        }
    }

    override suspend fun commissionOnNetworkDevice(pin: Long): MatterCommissionResponse? {
        return try {
            serverManager.webSocketRepository().commissionMatterDeviceOnNetwork(pin)
        } catch (e: Exception) {
            Log.e(TAG, "Error while executing server commissioning request", e)
            null
        }
    }
}
