package io.homeassistant.companion.android.sensors

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.homeassistant.companion.android.domain.integration.IntegrationUseCase
import io.homeassistant.companion.android.util.PermissionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class PhoneStateReceiver(
    private val integrationUseCase: IntegrationUseCase
) : BroadcastReceiver() {
    private val ioScope = CoroutineScope(Dispatchers.IO)
    private var updateJob: Job? = null
    override fun onReceive(context: Context, intent: Intent?) {
        if (PermissionManager.checkPhoneStatePermission(context)) {
            updateJob?.cancel()
            updateJob = ioScope.launch {
                SensorReceiver().updateSensors(context, integrationUseCase)
            }
        }
    }
}
