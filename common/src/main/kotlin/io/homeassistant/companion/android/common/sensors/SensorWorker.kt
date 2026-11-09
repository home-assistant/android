package io.homeassistant.companion.android.common.sensors

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.util.CHANNEL_SENSOR_WORKER
import io.homeassistant.companion.android.common.util.CheckLocalNetworkPermissionUseCase
import io.homeassistant.companion.android.common.util.SdkVersion
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Tells whether a long-running worker holds the foreground service that WorkManager shares between
 * all the workers, in which case [SensorWorker] must not add its time limited type to it.
 *
 * This is not about any worker in foreground, only the ones that have to outlive that time limit.
 * Which ones is app-specific, so each app module provides its own implementation through Hilt.
 */
fun interface WorkerForegroundStateProvider {
    suspend fun isAlreadyInForeground(): Boolean
}

class SensorWorker(private val context: Context, workerParams: WorkerParameters) :
    CoroutineWorker(context, workerParams) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    internal interface SensorWorkerEntryPoint {
        fun serverManager(): ServerManager
        fun checkLocalNetworkPermission(): CheckLocalNetworkPermissionUseCase
        fun lastUpdateManager(): LastUpdateManager
        fun sensorRepository(): SensorRepository
        fun sensorUpdater(): SensorUpdater
        fun workerForegroundStateProvider(): WorkerForegroundStateProvider
    }

    companion object {
        private const val TAG = "SensorWorker"
        private const val NOTIFICATION_ID = 42

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

    private val notificationManager = context.getSystemService<NotificationManager>()!!

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val entryPoint = EntryPointAccessors.fromApplication(context, SensorWorkerEntryPoint::class.java)
        val sensorRepository = entryPoint.sensorRepository()
        val serverManager = entryPoint.serverManager()
        val checkLocalNetworkPermission = entryPoint.checkLocalNetworkPermission()
        val lastUpdateManager = entryPoint.lastUpdateManager()
        val sensorUpdater = entryPoint.sensorUpdater()
        val workerForegroundStateProvider = entryPoint.workerForegroundStateProvider()

        val enabledSensorCount = sensorRepository.getEnabledCount()
        if (
            enabledSensorCount > 0 ||
            serverManager.servers().any {
                serverManager.integrationRepository(it.id).isHomeAssistantVersionAtLeast(2022, 6, 0)
            }
        ) {
            if (!checkLocalNetworkPermission()) {
                Timber.d("Skipping sensor update: ACCESS_LOCAL_NETWORK permission missing")
                return@withContext Result.success()
            }
            if (workerForegroundStateProvider.isAlreadyInForeground()) {
                // WorkManager shares one foreground service between all workers. Adding the time limited dataSync
                // type to it gets the worker holding it stopped by the system once the limit is reached.
                // https://github.com/home-assistant/android/issues/7761
                Timber.d("Updating all Sensors within the foreground service already running.")
            } else {
                startForeground()
            }

            val lastUpdateSensor = sensorRepository.get(LastUpdateManager.lastUpdate.id)
            if (lastUpdateSensor.any { it.enabled }) {
                lastUpdateManager.sendLastUpdate(TAG)
            }
            sensorUpdater.updateSensors()
        }

        // Cleanup orphaned sensors that may have been created by a slow or long running update
        // writing data when deleting the server.
        val currentServerIds = serverManager.servers().map { it.id }
        sensorRepository.removeSensorsExceptServers(currentServerIds)

        Result.success()
    }

    private suspend fun startForeground() {
        createNotificationChannel()
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_SENSOR_WORKER)
            .setSmallIcon(commonR.drawable.ic_stat_ic_notification)
            .setContentTitle(context.getString(commonR.string.updating_sensors))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        val foregroundInfo = ForegroundInfo(
            NOTIFICATION_ID,
            notification,
            if (SdkVersion.isAtLeast(Build.VERSION_CODES.Q)) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            },
        )
        try {
            setForeground(foregroundInfo)
            Timber.d("Updating all Sensors in foreground.")
        } catch (e: IllegalStateException) {
            // On Android 12+ we might encounter a ForegroundServiceStartNotAllowedException
            // depending on battery settings and trigger. Because the service also works in the
            // background, ignore it and continue (doesn't need to be logged as an exception).
            Timber.d(e, "Updating all Sensors in background.")
        }
    }

    private fun createNotificationChannel() {
        if (SdkVersion.isAtLeast(Build.VERSION_CODES.O)) {
            val notificationChannel = NotificationChannel(
                CHANNEL_SENSOR_WORKER,
                context.getString(commonR.string.sensor_updates),
                NotificationManager.IMPORTANCE_LOW,
            )
            notificationManager.createNotificationChannel(notificationChannel)
        }
    }
}
