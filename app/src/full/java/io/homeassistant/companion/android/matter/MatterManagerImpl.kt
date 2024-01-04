package io.homeassistant.companion.android.matter

import android.content.ComponentName
import android.content.Context
import android.content.IntentSender
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.google.android.gms.home.matter.Matter
import com.google.android.gms.home.matter.commissioning.CommissioningRequest
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.data.websocket.impl.entities.MatterCommissionResponse
import javax.inject.Inject

class MatterManagerImpl @Inject constructor(
    private val serverManager: ServerManager,
    private val packageManager: PackageManager
) : MatterManager {

    companion object {
        private const val TAG = "MatterManagerImpl"
    }

    override fun appSupportsCommissioning(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1 && !packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)

    override suspend fun coreSupportsCommissioning(serverId: Int): Boolean {
        if (!serverManager.isRegistered() || serverManager.getServer(serverId)?.user?.isAdmin != true) return false
        val config = serverManager.webSocketRepository(serverId).getConfig()
        return config != null && config.components.contains("matter")
    }

    override fun suppressDiscoveryBottomSheet(context: Context) {
        if (!appSupportsCommissioning()) return
        Matter.getCommissioningClient(context).suppressHalfSheetNotification()
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

    override suspend fun commissionDevice(code: String, serverId: Int): MatterCommissionResponse? {
        return try {
            serverManager.webSocketRepository(serverId).commissionMatterDevice(code)
        } catch (e: Exception) {
            Log.e(TAG, "Error while executing server commissioning request", e)
            null
        }
    }

    override suspend fun commissionOnNetworkDevice(pin: Long, ip: String, serverId: Int): MatterCommissionResponse? {
        return try {
            serverManager.webSocketRepository(serverId).commissionMatterDeviceOnNetwork(pin, ip)
        } catch (e: Exception) {
            Log.e(TAG, "Error while executing server commissioning request", e)
            null
        }
    }
}
