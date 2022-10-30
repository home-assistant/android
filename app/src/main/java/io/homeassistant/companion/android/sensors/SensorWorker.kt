package io.homeassistant.companion.android.sensors

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.common.sensors.SensorReceiverBase
import io.homeassistant.companion.android.common.sensors.SensorWorkerBase
import java.util.concurrent.TimeUnit

class SensorWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : SensorWorkerBase(appContext, workerParams) {

    companion object {
        fun start(context: Context) = start(
            context,
            determineUpdateFrequency(context)
        )

        fun start(
            context: Context,
            frequency: Int
        ) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED).build()

            val sensorWorker =
                PeriodicWorkRequestBuilder<SensorWorker>(frequency.toLong(), TimeUnit.MINUTES)
                    .setConstraints(constraints)
                    .build()

            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(TAG, ExistingPeriodicWorkPolicy.REPLACE, sensorWorker)
        }
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface SensorWorkerEntryPoint {
        fun integrationRepository(): IntegrationRepository
    }

    override val integrationUseCase: IntegrationRepository
        get() {
            return EntryPointAccessors.fromApplication(
                appContext,
                SensorWorkerEntryPoint::class.java
            )
                .integrationRepository()
        }

    override val sensorReceiver: SensorReceiverBase
        get() {
            return SensorReceiver()
        }
}
