package io.homeassistant.companion.android.sensors

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.homeassistant.companion.android.common.dagger.GraphComponentAccessor
import io.homeassistant.companion.android.domain.integration.IntegrationUseCase
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class NextAlarmReceiver() : BroadcastReceiver() {

    @Inject
    lateinit var integrationUseCase: IntegrationUseCase
    private val ioScope = CoroutineScope(Dispatchers.IO)
    var updateJob: Job? = null
    override fun onReceive(context: Context, intent: Intent) {
        val isBootIntent = Intent.ACTION_BOOT_COMPLETED.equals(intent.action, ignoreCase = true)
        val isNextAlarmIntent =
            AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED.equals(intent.action, ignoreCase = true)
        if (!isBootIntent && !isNextAlarmIntent) {
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
