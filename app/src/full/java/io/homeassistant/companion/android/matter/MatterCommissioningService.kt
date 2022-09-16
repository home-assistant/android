package io.homeassistant.companion.android.matter

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.google.android.gms.home.matter.commissioning.CommissioningCompleteMetadata
import com.google.android.gms.home.matter.commissioning.CommissioningRequestMetadata
import com.google.android.gms.home.matter.commissioning.CommissioningService

class MatterCommissioningService : Service(), CommissioningService.Callback {

    companion object {
        private const val TAG = "MatterCommissioningServ"
    }

    private lateinit var commissioningServiceDelegate: CommissioningService

    override fun onCreate() {
        super.onCreate()
        commissioningServiceDelegate = CommissioningService.Builder(this).setCallback(this).build()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return commissioningServiceDelegate.asBinder()
    }

    override fun onCommissioningRequested(metadata: CommissioningRequestMetadata) {
        Log.d(TAG, "Received request to commission Matter device")
        // Because commissioning is handled via the frontend, send the passcode over in the result
        commissioningServiceDelegate.sendCommissioningComplete(
            CommissioningCompleteMetadata.Builder().setToken(metadata.passcode.toString()).build()
        ).addOnCompleteListener {
            Log.d(TAG, "Successfully sent commissioning complete")
        }.addOnFailureListener { e ->
            Log.e(TAG, "Failed sending commissioning complete", e)
        }
    }
}
