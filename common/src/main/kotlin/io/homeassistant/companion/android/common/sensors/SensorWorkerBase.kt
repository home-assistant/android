package io.homeassistant.companion.android.common.sensors

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.util.CHANNEL_SENSOR_WORKER
import io.homeassistant.companion.android.database.AppDatabase
import java.lang.IllegalStateException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

abstract class SensorWorkerBase(val appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    protected abstract val serverManager: ServerManager
    protected abstract val sensorReceiver: SensorReceiverBase

    companion object {
        const val TAG = "SensorWorker"
        const val NOTIFICATION_ID = 42
    }

    private val notificationManager = appContext.getSystemService<NotificationManager>()!!

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val sensorDao = AppDatabase.getInstance(applicationContext).sensorDao()
        val enabledSensorCount = sensorDao.getEnabledCount() ?: 0
        if (
            enabledSensorCount > 0 ||
            serverManager.defaultServers.any {
                serverManager.integrationRepository(it.id).isHomeAssistantVersionAtLeast(2022, 6, 0)
            }
        ) {
            createNotificationChannel()
            val notification = NotificationCompat.Builder(applicationContext, CHANNEL_SENSOR_WORKER)
                .setSmallIcon(commonR.drawable.ic_stat_ic_notification)
                .setContentTitle(appContext.getString(commonR.string.updating_sensors))
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()

            val foregroundInfo = ForegroundInfo(
                NOTIFICATION_ID,
                notification,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
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

            val lastUpdateSensor = sensorDao.get(LastUpdateManager.lastUpdate.id)
            if (lastUpdateSensor.any { it.enabled }) {
                LastUpdateManager().sendLastUpdate(appContext, TAG)
            }
            sensorReceiver.updateSensors(appContext, serverManager, sensorDao, null)
        }

        // Cleanup orphaned sensors that may have been created by a slow or long running update
        // writing data when deleting the server.
        val currentServerIds = serverManager.defaultServers.map { it.id }
        val orphanedSensors = sensorDao.getAllExceptServer(currentServerIds)
        if (orphanedSensors.any()) {
            Timber.i("Cleaning up ${orphanedSensors.size} orphaned sensor entries")
            orphanedSensors.forEach {
                sensorDao.removeSensor(it.id, it.serverId)
            }
        }

        Result.success()
    }

    protected fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationChannel = NotificationChannel(
                CHANNEL_SENSOR_WORKER,
                appContext.getString(commonR.string.sensor_updates),
                NotificationManager.IMPORTANCE_LOW,
            )
            notificationManager.createNotificationChannel(notificationChannel)
        }
    }
}
