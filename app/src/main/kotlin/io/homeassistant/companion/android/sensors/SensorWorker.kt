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
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.sensors.LastUpdateManager
import io.homeassistant.companion.android.common.sensors.SensorReceiverBase
import io.homeassistant.companion.android.common.sensors.SensorWorkerBase
import io.homeassistant.companion.android.common.util.CheckLocalNetworkPermissionUseCase
import java.util.concurrent.TimeUnit

class SensorWorker(appContext: Context, workerParams: WorkerParameters) : SensorWorkerBase(appContext, workerParams) {

    companion object {
        fun start(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED).build()

            val sensorWorker =
                PeriodicWorkRequestBuilder<SensorWorker>(15, TimeUnit.MINUTES)
                    .setConstraints(constraints)
                    .build()

            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(TAG, ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE, sensorWorker)
        }
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface SensorWorkerEntryPoint {
        fun serverManager(): ServerManager
        fun checkLocalNetworkPermission(): CheckLocalNetworkPermissionUseCase
        fun lastUpdateManager(): LastUpdateManager
    }

    private val entryPoint by lazy {
        EntryPointAccessors.fromApplication(appContext, SensorWorkerEntryPoint::class.java)
    }

    override val serverManager: ServerManager
        get() = entryPoint.serverManager()

    override val sensorReceiver: SensorReceiverBase
        get() = SensorReceiver()

    override val checkLocalNetworkPermission: CheckLocalNetworkPermissionUseCase
        get() = entryPoint.checkLocalNetworkPermission()

    override val lastUpdateManager: LastUpdateManager
        get() = entryPoint.lastUpdateManager()
}
