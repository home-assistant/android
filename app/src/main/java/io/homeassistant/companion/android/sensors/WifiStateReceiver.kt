package io.homeassistant.companion.android.sensors

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import io.homeassistant.companion.android.common.dagger.GraphComponentAccessor
import io.homeassistant.companion.android.domain.integration.IntegrationUseCase
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class WifiStateReceiver() : BroadcastReceiver() {

    @Inject
    lateinit var integrationUseCase: IntegrationUseCase
    private val ioScope = CoroutineScope(Dispatchers.IO)
    var updateJob: Job? = null
    override fun onReceive(context: Context, intent: Intent) {

        val wifiState = intent.getIntExtra(
            WifiManager.EXTRA_WIFI_STATE,
            WifiManager.WIFI_STATE_UNKNOWN
        )

        if (wifiState < 0) {
            return
        }

        DaggerSensorComponent
            .builder()
            .appComponent((context.applicationContext as GraphComponentAccessor).appComponent)
            .build()
            .inject(this)

        updateJob?.cancel()
        updateJob = ioScope.launch {
            AllSensorsUpdaterImpl(integrationUseCase, context).updateSensors()
        }
    }
}
