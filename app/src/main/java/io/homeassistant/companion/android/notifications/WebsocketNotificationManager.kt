package io.homeassistant.companion.android.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Context.NOTIFICATION_SERVICE
import android.hardware.display.DisplayManager
import android.os.Build
import android.util.Log
import android.view.Display
import androidx.core.app.NotificationCompat
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
import io.homeassistant.companion.android.common.R
import io.homeassistant.companion.android.common.data.websocket.WebSocketRepository
import io.homeassistant.companion.android.database.AppDatabase
import io.homeassistant.companion.android.database.settings.LocalNotificationSetting
import kotlinx.coroutines.ExperimentalCoroutinesApi
import java.util.concurrent.TimeUnit

@ExperimentalCoroutinesApi
class WebsocketNotificationManager(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "WebSockNotifManager"
        private const val CHANNEL_ID = "Websocket Notifications"
        private const val NOTIFICATION_ID = 65423

        fun start(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val websocketNotifications =
                PeriodicWorkRequestBuilder<WebsocketNotificationManager>(15, TimeUnit.MINUTES)
                    .setConstraints(constraints)
                    .build()

            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    TAG,
                    ExistingPeriodicWorkPolicy.REPLACE,
                    websocketNotifications
                )
        }
    }

    private val notificationManager =
        applicationContext.getSystemService(NOTIFICATION_SERVICE) as NotificationManager

    private val entryPoint = EntryPointAccessors
        .fromApplication(applicationContext, WebsocketNotificationManagerEntryPoint::class.java)

    private val websocketRepository: WebSocketRepository = entryPoint.websocketRepository()
    private val messagingManager: MessagingManager = entryPoint.messagingManager()

    private val settingsDao = AppDatabase.getInstance(appContext).settingsDao()

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WebsocketNotificationManagerEntryPoint {
        fun websocketRepository(): WebSocketRepository
        fun messagingManager(): MessagingManager
    }

    override suspend fun doWork(): Result {
        // should we be running....
        val dm = applicationContext.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val displayOff = dm.displays.all { it.state == Display.STATE_OFF }
        val setting = settingsDao.get(0)?.localNotificationSetting ?: LocalNotificationSetting.SCREEN_ON
        if (setting == LocalNotificationSetting.NEVER) {
            return Result.success()
        } else if (displayOff && settingsDao.get(0)?.localNotificationSetting == LocalNotificationSetting.SCREEN_ON) {
            return Result.success()
        }

        Log.d(TAG, "Starting to listen for Websocket Notifications")
        createNotificationChannel()
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_ic_notification)
            .setContentTitle(applicationContext.getString(R.string.websocket_listening))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        setForeground(ForegroundInfo(NOTIFICATION_ID, notification))

        websocketRepository.getNotifications()?.collect {
            if (it.containsKey("hass_confirm_id"))
                websocketRepository.ackNotification(it["hass_confirm_id"].toString())
            val flattened = mutableMapOf<String, String>()
            if (it.containsKey("data")) {
                for ((key, value) in it["data"] as Map<*, *>) {
                    if (key == "actions" && value is List<*>) {
                        value.forEachIndexed { i, action ->
                            if (action is Map<*, *>) {
                                flattened["action_${i + 1}_key"] = action["action"].toString()
                                flattened["action_${i + 1}_title"] = action["title"].toString()
                                flattened["action_${i + 1}_uri"] = action["uri"].toString()
                            }
                        }
                    } else {
                        flattened[key.toString()] = value.toString()
                    }
                }
            }
            // Message and title are in the root unlike all the others.
            listOf("message", "title").forEach { key ->
                if (it.containsKey(key))
                    flattened[key] = it[key].toString()
            }
            messagingManager.handleMessage(flattened)
        }

        return Result.success()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            var notificationChannel =
                notificationManager.getNotificationChannel(CHANNEL_ID)
            if (notificationChannel == null) {
                notificationChannel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_ID,
                    NotificationManager.IMPORTANCE_LOW
                )
                notificationManager.createNotificationChannel(notificationChannel)
            }
        }
    }
}
