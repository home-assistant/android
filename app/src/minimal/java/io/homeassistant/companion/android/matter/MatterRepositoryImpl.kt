package io.homeassistant.companion.android.matter

import android.content.Context
import android.content.IntentSender
import javax.inject.Inject

class MatterRepositoryImpl @Inject constructor() : MatterRepository {

    // Matter support currently depends on Google Play Services,
    // and as a result Matter is not supported with the minimal flavor

    override fun appSupportsCommissioning(): Boolean = false

    override fun startNewCommissioningFlow(
        context: Context,
        onSuccess: (IntentSender) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        onFailure(IllegalStateException("Matter commissioning is not supported with the minimal flavor"))
    }

    override suspend fun commissionOnNetworkDevice(pin: Int): Boolean = false
}
