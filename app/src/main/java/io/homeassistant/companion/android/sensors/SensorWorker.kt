package io.homeassistant.companion.android.sensors

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
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

class SensorWorker(private val appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    companion object {
        fun start(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED).build()

            val sensorWorker =
                PeriodicWorkRequestBuilder<SensorWorker>(15, TimeUnit.MINUTES)
                    .setConstraints(constraints)
                    .addTag("sensors")
                    .build()

            WorkManager.getInstance(context).cancelAllWorkByTag("sensors")
            WorkManager.getInstance(context).enqueue(sensorWorker)
        }
    }

    @Inject
    lateinit var integrationUseCase: IntegrationUseCase

    init {
        DaggerSensorComponent.builder()
            .appComponent((appContext as GraphComponentAccessor).appComponent)
            .build()
            .inject(this)
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {

        val sensorManagers = arrayOf(
            BatterySensorManager(),
            NetworkSensorManager()
        )

        registerSensors(sensorManagers)

        val success = integrationUseCase.updateSensors(
            sensorManagers.flatMap { it.getSensors(appContext) }.toTypedArray()
        )

        // We failed to update a sensor, we should register all the sensors again.
        if (!success) {
            registerSensors(sensorManagers)
        }

        Result.success()
    }

    private suspend fun registerSensors(sensorManagers: Array<SensorManager>) {

        sensorManagers.flatMap {
            it.getSensorRegistrations(appContext)
        }.forEach {
            // I want to call this async but because of the way we need to store the
            // fact we have registered it we can't
            integrationUseCase.registerSensor(it)
        }
    }
}
