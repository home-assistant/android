package io.homeassistant.companion.android.sensors

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import io.homeassistant.companion.android.common.dagger.GraphComponentAccessor
import io.homeassistant.companion.android.domain.integration.IntegrationUseCase
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext

class SensorWorker(private val appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    @Inject
    lateinit var integrationUseCase: IntegrationUseCase

    init {
        DaggerSensorComponent.builder()
            .appComponent((appContext as GraphComponentAccessor).appComponent)
            .build()
            .inject(this)
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {

        val batterySensorManager = BatterySensorManager()
        batterySensorManager.getSensorRegistrations(appContext).map {
            async { integrationUseCase.registerSensor(it) }
        }.awaitAll()

        integrationUseCase.updateSensors(batterySensorManager.getSensors(appContext).toTypedArray())

        Result.success()
    }
}
