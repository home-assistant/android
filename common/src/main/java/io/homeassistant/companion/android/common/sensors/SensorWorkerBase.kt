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
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.database.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import io.homeassistant.companion.android.common.R as commonR

abstract class SensorWorkerBase(
    val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    protected abstract val integrationUseCase: IntegrationRepository
    protected abstract val sensorReceiver: SensorReceiverBase

    companion object {
        const val TAG = "SensorWorker"
        const val channelId = "Sensor Worker"
        const val NOTIFICATION_ID = 42
    }

    private val notificationManager = appContext.getSystemService<NotificationManager>()!!

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val sensorDao = AppDatabase.getInstance(applicationContext).sensorDao()
        val enabledSensorCount = sensorDao.getEnabledCount() ?: 0
        if (enabledSensorCount > 0) {
            Log.d(TAG, "Updating all Sensors.")
            createNotificationChannel()
            val notification = NotificationCompat.Builder(applicationContext, channelId)
                .setSmallIcon(commonR.drawable.ic_stat_ic_notification)
                .setContentTitle(appContext.getString(commonR.string.updating_sensors))
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()

            val foregroundInfo = ForegroundInfo(NOTIFICATION_ID, notification)
            setForeground(foregroundInfo)
            val lastUpdateSensor = sensorDao.get(LastUpdateManager.lastUpdate.id)
            if (lastUpdateSensor != null) {
                if (lastUpdateSensor.enabled)
                    LastUpdateManager().sendLastUpdate(appContext, TAG)
            }
            sensorReceiver.updateSensors(appContext, integrationUseCase, null)
        }
        Result.success()
    }

    protected fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            var notificationChannel =
                notificationManager.getNotificationChannel(channelId)
            if (notificationChannel == null) {
                notificationChannel = NotificationChannel(
                    channelId, TAG, NotificationManager.IMPORTANCE_LOW
                )
                notificationManager.createNotificationChannel(notificationChannel)
            }
        }
    }
}
