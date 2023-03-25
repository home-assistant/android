package io.homeassistant.companion.android.common.sensors

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.util.sensorWorkerChannel
import io.homeassistant.companion.android.database.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.lang.IllegalStateException
import io.homeassistant.companion.android.common.R as commonR

abstract class SensorWorkerBase(
    val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

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
            val notification = NotificationCompat.Builder(applicationContext, sensorWorkerChannel)
                .setSmallIcon(commonR.drawable.ic_stat_ic_notification)
                .setContentTitle(appContext.getString(commonR.string.updating_sensors))
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()

            val foregroundInfo = ForegroundInfo(NOTIFICATION_ID, notification)
            try {
                setForeground(foregroundInfo)
                Log.d(TAG, "Updating all Sensors in foreground.")
            } catch (e: IllegalStateException) {
                // On Android 12+ we might encounter a ForegroundServiceStartNotAllowedException
                // depending on battery settings and trigger. Because the service also works in the
                // background, ignore it and continue (doesn't need to be logged as an exception).
                Log.d(TAG, "Updating all Sensors in background.", e)
            }

            val lastUpdateSensor = sensorDao.get(LastUpdateManager.lastUpdate.id)
            if (lastUpdateSensor.any { it.enabled }) {
                LastUpdateManager().sendLastUpdate(appContext, TAG)
            }
            sensorReceiver.updateSensors(appContext, serverManager, sensorDao, null)
        }
        Result.success()
    }

    protected fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            var notificationChannel =
                notificationManager.getNotificationChannel(sensorWorkerChannel)
            if (notificationChannel == null) {
                notificationChannel = NotificationChannel(
                    sensorWorkerChannel,
                    TAG,
                    NotificationManager.IMPORTANCE_LOW
                )
                notificationManager.createNotificationChannel(notificationChannel)
            }
        }
    }
}
