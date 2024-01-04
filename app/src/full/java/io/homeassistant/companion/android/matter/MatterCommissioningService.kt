package io.homeassistant.companion.android.matter

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.google.android.gms.home.matter.commissioning.CommissioningCompleteMetadata
import com.google.android.gms.home.matter.commissioning.CommissioningRequestMetadata
import com.google.android.gms.home.matter.commissioning.CommissioningService
import com.google.android.gms.home.matter.commissioning.CommissioningService.CommissioningError
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.common.data.servers.ServerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MatterCommissioningService : Service(), CommissioningService.Callback {

    companion object {
        private const val TAG = "MatterCommissioningServ"
    }

    @Inject
    lateinit var serverManager: ServerManager

    @Inject
    lateinit var matterManager: MatterManager

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())

    private lateinit var commissioningServiceDelegate: CommissioningService

    override fun onCreate() {
        super.onCreate()
        commissioningServiceDelegate = CommissioningService.Builder(this).setCallback(this).build()
    }

    override fun onBind(intent: Intent?): IBinder {
        return commissioningServiceDelegate.asBinder()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    override fun onCommissioningRequested(metadata: CommissioningRequestMetadata) {
        Log.d(TAG, "Received request to commission Matter device")

        serviceScope.launch {
            // This service is used from the frontend, where the server requests commissioning. As a
            // result, we can assume the server ID is the active one.
            val serverId = serverManager.getServer()?.id ?: run {
                commissioningServiceDelegate.sendCommissioningError(CommissioningError.OTHER)
                return@launch
            }
            val result = matterManager.commissionOnNetworkDevice(metadata.passcode, metadata.networkLocation.formattedIpAddress, serverId)
            Log.d(TAG, "Server commissioning was ${if (result?.success == true) "successful" else "not successful (${result?.errorCode})"}")

            if (result?.success == true) {
                commissioningServiceDelegate.sendCommissioningComplete(
                    CommissioningCompleteMetadata.Builder().build()
                )
            } else {
                commissioningServiceDelegate.sendCommissioningError(CommissioningError.OTHER)
            }
        }
    }
}
