package io.homeassistant.companion.android.matter

import android.content.ComponentName
import android.content.Context
import android.content.IntentSender
import android.os.Build
import android.util.Log
import com.google.android.gms.home.matter.Matter
import com.google.android.gms.home.matter.commissioning.CommissioningRequest
import io.homeassistant.companion.android.common.data.websocket.WebSocketRepository
import javax.inject.Inject

class MatterManagerImpl @Inject constructor(
    private val websocketRepository: WebSocketRepository
) : MatterManager {

    companion object {
        private const val TAG = "MatterRepositoryImpl"
    }

    override fun appSupportsCommissioning(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1

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

    override suspend fun commissionOnNetworkDevice(pin: Int): Boolean {
        return try {
            websocketRepository.commissionMatterDeviceOnNetwork(pin)
        } catch (e: Exception) {
            Log.e(TAG, "Error while executing server commissioning request", e)
            false
        }
    }
}
