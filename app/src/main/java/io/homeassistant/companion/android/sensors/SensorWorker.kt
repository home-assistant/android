package io.homeassistant.companion.android.sensors

import android.content.Context
import androidx.work.CoroutineWorker
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

            val sensorWorker =
                PeriodicWorkRequestBuilder<SensorWorker>(15, TimeUnit.MINUTES)
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

        // If we have no sensors enabled we don't need the worker.
        if (integrationUseCase.getEnabledSensors().isNullOrEmpty()) {
            WorkManager.getInstance(appContext).cancelWorkById(id)
        }

        val sensorManagers = ArrayList<SensorManager>()

        if (integrationUseCase.getEnabledSensors()?.contains("battery") == true) {
            sensorManagers.add(BatterySensorManager())
        }

        if (integrationUseCase.getEnabledSensors()?.contains("network") == true) {
            sensorManagers.add(NetworkSensorManager())
        }

        sensorManagers.flatMap {
            it.getSensorRegistrations(appContext)
        }.forEach {
            // I want to call this async but because of the way we need to store the
            // fact we have registered it we can't
            integrationUseCase.registerSensor(it)
        }

        integrationUseCase.updateSensors(
            sensorManagers.flatMap { it.getSensors(appContext) }.toTypedArray()
        )

        Result.success()
    }
}
