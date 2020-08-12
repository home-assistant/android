package io.homeassistant.companion.android.sensors

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.homeassistant.companion.android.domain.integration.IntegrationUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ChargingBroadcastReceiver(
    private val integrationUseCase: IntegrationUseCase
) : BroadcastReceiver() {
    private val ioScope = CoroutineScope(Dispatchers.IO)
    private var updateJob: Job? = null
    override fun onReceive(context: Context, intent: Intent?) {
        updateJob?.cancel()
        updateJob = ioScope.launch {
            AllSensorsUpdaterImpl(integrationUseCase, context).updateSensors()
            // Add a 5 second delay to perform another update so charging state updates completely.
            // This is necessary as the system needs a few seconds to verify the charger.
            delay(5000L)
            AllSensorsUpdaterImpl(integrationUseCase, context).updateSensors()
        }
    }
}
