package io.homeassistant.companion.android.sensors

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.homeassistant.companion.android.common.dagger.GraphComponentAccessor
import io.homeassistant.companion.android.domain.integration.IntegrationUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

class BluetoothReceiver() : BroadcastReceiver() {

    @Inject
    lateinit var integrationUseCase: IntegrationUseCase
    private val ioScope = CoroutineScope(Dispatchers.IO)
    private var updateJob: Job? = null
    override fun onReceive(context: Context, intent: Intent?) {
        DaggerSensorComponent
            .builder()
            .appComponent((context.applicationContext as GraphComponentAccessor).appComponent)
            .build()
            .inject(this)

        updateJob?.cancel()
        updateJob = ioScope.launch {
            SensorReceiver().updateSensors(context, integrationUseCase)
        }
    }
}
