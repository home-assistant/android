package io.homeassistant.companion.android.sensors

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Context.NOTIFICATION_SERVICE
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.dagger.GraphComponentAccessor
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
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
        const val channelId = "Sensor Worker"
        const val NOTIFICATION_ID = 42

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
    lateinit var integrationUseCase: IntegrationRepository

    private val notificationManager = appContext.getSystemService(NOTIFICATION_SERVICE) as NotificationManager

    init {
        DaggerSensorComponent.builder()
            .appComponent((appContext.applicationContext as GraphComponentAccessor).appComponent)
            .build()
            .inject(this)
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Log.d(TAG, "Updating all Sensors.")
        createNotificationChannel()
        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(R.drawable.ic_stat_ic_notification)
            .setContentTitle(appContext.getString(R.string.updating_sensors))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        val foregroundInfo = ForegroundInfo(NOTIFICATION_ID, notification)
        setForeground(foregroundInfo)

        SensorReceiver().updateSensors(appContext, integrationUseCase)
        Result.success()
    }

    private fun createNotificationChannel() {
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
