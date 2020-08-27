package io.homeassistant.companion.android.sensors

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.homeassistant.companion.android.common.dagger.GraphComponentAccessor
import io.homeassistant.companion.android.domain.integration.IntegrationUseCase
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SensorWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {
    companion object {
        private const val TAG = "SensorWorker"
        fun start(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED).build()

            val sensorWorker =
                PeriodicWorkRequestBuilder<SensorWorker>(15, TimeUnit.MINUTES)
                    .setConstraints(constraints)
                    .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(TAG, ExistingPeriodicWorkPolicy.REPLACE, sensorWorker)
        }
    }

    @Inject
    lateinit var integrationUseCase: IntegrationUseCase

    init {
        DaggerSensorComponent.builder()
            .appComponent((appContext.applicationContext as GraphComponentAccessor).appComponent)
            .build()
            .inject(this)
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Log.d(TAG, "Updating all Sensors.")
        SensorReceiver().updateSensors(appContext, integrationUseCase)
        Result.success()
    }
}
