package io.homeassistant.companion.android.websocket

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.util.Log
import android.view.Display
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import io.homeassistant.companion.android.BuildConfig
import io.homeassistant.companion.android.common.R
import io.homeassistant.companion.android.common.data.websocket.WebSocketRepository
import io.homeassistant.companion.android.database.AppDatabase
import io.homeassistant.companion.android.database.settings.WebsocketSetting
import io.homeassistant.companion.android.notifications.MessagingManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

@ExperimentalCoroutinesApi
class WebsocketManager(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "WebSockManager"
        private const val SOURCE = "Websocket"
        private const val CHANNEL_ID = "Websocket"
        private const val NOTIFICATION_ID = 65423
        private val DEFAULT_WEBSOCKET_SETTING = if (BuildConfig.FLAVOR == "full") WebsocketSetting.SCREEN_ON else WebsocketSetting.ALWAYS

        fun start(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val websocketNotifications =
                PeriodicWorkRequestBuilder<WebsocketManager>(15, TimeUnit.MINUTES)
                    .setConstraints(constraints)
                    .build()

            val workManager = WorkManager.getInstance(context)
            val workInfo = workManager.getWorkInfosForUniqueWork(TAG).get().firstOrNull()

            if (workInfo == null || workInfo.state.isFinished || workInfo.state == WorkInfo.State.ENQUEUED) {
                workManager.enqueueUniquePeriodicWork(
                    TAG,
                    ExistingPeriodicWorkPolicy.REPLACE,
                    websocketNotifications
                )
            } else {
                workManager.enqueueUniquePeriodicWork(
                    TAG,
                    ExistingPeriodicWorkPolicy.KEEP,
                    websocketNotifications
                )
            }
        }
    }

    private val notificationManager = applicationContext.getSystemService<NotificationManager>()!!

    private val entryPoint = EntryPointAccessors
        .fromApplication(applicationContext, WebsocketManagerEntryPoint::class.java)

    private val websocketRepository: WebSocketRepository = entryPoint.websocketRepository()
    private val messagingManager: MessagingManager = entryPoint.messagingManager()

    private val settingsDao = AppDatabase.getInstance(appContext).settingsDao()

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WebsocketManagerEntryPoint {
        fun websocketRepository(): WebSocketRepository
        fun messagingManager(): MessagingManager
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        if (!shouldWeRun()) {
            return@withContext Result.success()
        }

        Log.d(TAG, "Starting to listen to Websocket")
        createNotification()

        // Start listening for notifications
        val job = launch { collectNotifications() }

        // play ping pong to ensure we have a connection.
        do {
            delay(30000)
        } while (job.isActive && isActive && shouldWeRun() && websocketRepository.sendPing())

        job.cancel()

        Log.d(TAG, "Done listening to Websocket")

        return@withContext Result.success()
    }

    private fun shouldWeRun(): Boolean {
        val dm = applicationContext.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val displayOff = dm.displays.all { it.state == Display.STATE_OFF }
        val setting = settingsDao.get(0)?.websocketSetting ?: DEFAULT_WEBSOCKET_SETTING
        if (setting == WebsocketSetting.NEVER) {
            return false
        } else if (displayOff && setting == WebsocketSetting.SCREEN_ON) {
            return false
        }

        return true
    }

    private suspend fun collectNotifications() {
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
            messagingManager.handleMessage(flattened, SOURCE)
        }
    }

    private suspend fun createNotification() {
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

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_ic_notification)
            .setContentTitle(applicationContext.getString(R.string.websocket_listening))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setGroup(CHANNEL_ID)
            .build()
        setForeground(ForegroundInfo(NOTIFICATION_ID, notification))
    }
}
