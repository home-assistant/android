package io.homeassistant.companion.android.sensors

import android.content.Context
import androidx.work.*
import io.homeassistant.companion.android.common.dagger.GraphComponentAccessor
import io.homeassistant.companion.android.common.sensors.SensorReceiverBase
import io.homeassistant.companion.android.common.sensors.SensorWorkerBase
import java.util.concurrent.TimeUnit

class SensorWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : SensorWorkerBase(appContext, workerParams) {

    init {
        DaggerSensorComponent.builder()
            .appComponent((appContext.applicationContext as GraphComponentAccessor).appComponent)
            .build()
            .inject(this)
    }

    companion object {
        fun start(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED).build()

            val sensorWorker =
                PeriodicWorkRequestBuilder<SensorWorker>(15, TimeUnit.MINUTES)
                    .setConstraints(constraints)
                    .build()

            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(TAG, ExistingPeriodicWorkPolicy.REPLACE, sensorWorker)
        }
    }

    override fun createSensorReceiver(): SensorReceiverBase {
        return SensorReceiver()
    }
}
