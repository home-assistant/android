package io.homeassistant.companion.android.matter

import android.content.Context
import android.content.IntentSender
import androidx.activity.result.ActivityResult

object MatterCommissioningHelper {

    fun startNewCommissioningFlow(
        context: Context,
        onSuccess: (IntentSender) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        onFailure(IllegalStateException("Matter commissioning is not supported with the minimal flavor"))
    }

    fun getPasscodeFromCommissioningResult(activityResult: ActivityResult): String? {
        return null
    }
}
