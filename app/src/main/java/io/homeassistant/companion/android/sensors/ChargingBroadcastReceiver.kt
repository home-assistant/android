package io.homeassistant.companion.android.sensors

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import io.homeassistant.companion.android.domain.integration.IntegrationUseCase
import java.lang.Exception
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class ChargingBroadcastReceiver @Inject constructor(
    private val integrationUseCase: IntegrationUseCase
) : BroadcastReceiver() {
    private val ioScope = CoroutineScope(Dispatchers.IO)
    private var updateJob: Job? = null
    override fun onReceive(context: Context, intent: Intent?) {
        updateJob?.cancel()
        updateJob = ioScope.launch {
            val batterySensorManager = BatterySensorManager().registerSensors(context)
            integrationUseCase.updateSensors(
                batterySensorManager.getSensors(context).toTypedArray()
            )
        }
    }

    private suspend fun BatterySensorManager.registerSensors(context: Context) = apply {
        getSensorRegistrations(context).forEach {
            try {
                integrationUseCase.registerSensor(it)
            } catch (e: Exception) {
                Log.e(this::class.simpleName, "Issue registering sensor: ${it.uniqueId}", e)
            }
        }
    }
}
