package io.homeassistant.companion.android.matter

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.IntentSender
import android.os.Build
import androidx.activity.result.ActivityResult
import com.google.android.gms.home.matter.Matter
import com.google.android.gms.home.matter.commissioning.CommissioningRequest
import com.google.android.gms.home.matter.commissioning.CommissioningResult

object MatterCommissioningHelper {

    fun startNewCommissioningFlow(
        context: Context,
        onSuccess: (IntentSender) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
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

    fun getPasscodeFromCommissioningResult(activityResult: ActivityResult): String? {
        return if (activityResult.resultCode == Activity.RESULT_OK) {
            val parsed = CommissioningResult.fromIntentSenderResult(activityResult.resultCode, activityResult.data)
            parsed.token
        } else {
            null
        }
    }
}
