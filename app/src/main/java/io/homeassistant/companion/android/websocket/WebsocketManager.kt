package io.homeassistant.companion.android.websocket

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ForegroundInfo
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
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.util.websocketChannel
import io.homeassistant.companion.android.common.util.websocketIssuesChannel
import io.homeassistant.companion.android.database.settings.SettingsDao
import io.homeassistant.companion.android.database.settings.WebsocketSetting
import io.homeassistant.companion.android.notifications.MessagingManager
import io.homeassistant.companion.android.settings.SettingsActivity
import io.homeassistant.companion.android.webview.WebViewActivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.lang.IllegalStateException
import java.util.concurrent.TimeUnit

class WebsocketManager(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "WebSockManager"
        private const val SOURCE = "Websocket"
        private const val NOTIFICATION_ID = 65423
        private const val NOTIFICATION_RESTRICTED_ID = 65424
        private val DEFAULT_WEBSOCKET_SETTING = if (BuildConfig.FLAVOR == "full") WebsocketSetting.NEVER else WebsocketSetting.ALWAYS

        fun start(context: Context) {
            val websocketNotifications =
                PeriodicWorkRequestBuilder<WebsocketManager>(15, TimeUnit.MINUTES)
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

    private val serverManager: ServerManager = entryPoint.serverManager()
    private val messagingManager: MessagingManager = entryPoint.messagingManager()
    private val settingsDao: SettingsDao = entryPoint.settingsDao()

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WebsocketManagerEntryPoint {
        fun serverManager(): ServerManager
        fun messagingManager(): MessagingManager
        fun settingsDao(): SettingsDao
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        if (!shouldWeRun()) {
            return@withContext Result.success()
        }

        if (!createNotification()) {
            return@withContext Result.success()
        }

        // Start listening for notifications
        Log.d(TAG, "Starting to listen to Websocket")
        val job = launch { collectNotifications() }

        // play ping pong to ensure we have a connection.
        do {
            delay(30000)
        } while (job.isActive && isActive && shouldWeRun() && serverManager.webSocketRepository().sendPing())

        job.cancel()

        Log.d(TAG, "Done listening to Websocket")

        return@withContext Result.success()
    }

    @Suppress("DEPRECATION")
    private fun shouldWeRun(): Boolean {
        // Check for connectivity but not internet access, based on WorkManager's NetworkConnectedController API <26
        val connectivityManager = applicationContext.getSystemService<ConnectivityManager>()
        val networkInfo = connectivityManager?.activeNetworkInfo
        val powerManager = applicationContext.getSystemService<PowerManager>()!!
        val displayOff = !powerManager.isInteractive
        val setting = settingsDao.get(0)?.websocketSetting ?: DEFAULT_WEBSOCKET_SETTING
        val isHome = serverManager.getServer()?.connection?.isInternal() == true
        return when {
            (setting == WebsocketSetting.NEVER) -> false
            (networkInfo != null && !networkInfo.isConnected) -> false
            !serverManager.isRegistered() -> false
            (displayOff && setting == WebsocketSetting.SCREEN_ON) -> false
            (!isHome && setting == WebsocketSetting.HOME_WIFI) -> false
            else -> true
        }
    }

    private suspend fun collectNotifications() {
        serverManager.webSocketRepository().getNotifications()?.collect {
            if (it.containsKey("hass_confirm_id"))
                serverManager.webSocketRepository().ackNotification(it["hass_confirm_id"].toString())
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

    /**
     * Create a notification to start the service as a foreground service.
     *
     * @return `true` if the foreground service was started
     */
    private suspend fun createNotification(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            var notificationChannel =
                notificationManager.getNotificationChannel(websocketChannel)
            if (notificationChannel == null) {
                notificationChannel = NotificationChannel(
                    websocketChannel,
                    applicationContext.getString(R.string.websocket_setting_name),
                    NotificationManager.IMPORTANCE_LOW
                )
                notificationManager.createNotificationChannel(notificationChannel)
            }
        }

        val intent = WebViewActivity.newInstance(applicationContext)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        intent.addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
        intent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val settingIntent = SettingsActivity.newInstance(applicationContext)
        settingIntent.putExtra("fragment", "websocket")
        settingIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        settingIntent.addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
        settingIntent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
        val settingPendingIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            settingIntent,
            PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(applicationContext, websocketChannel)
            .setSmallIcon(R.drawable.ic_stat_ic_notification)
            .setContentTitle(applicationContext.getString(R.string.websocket_listening))
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setGroup(websocketChannel)
            .addAction(
                io.homeassistant.companion.android.R.drawable.ic_websocket,
                applicationContext.getString(R.string.settings),
                settingPendingIntent
            )
            .build()
        return try {
            setForeground(ForegroundInfo(NOTIFICATION_ID, notification))
            true
        } catch (e: IllegalStateException) {
            if (e is CancellationException) return false

            Log.e(TAG, "Unable to setForeground due to restrictions", e)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (notificationManager.getNotificationChannel(websocketIssuesChannel) == null) {
                    val restrictedNotificationChannel = NotificationChannel(
                        websocketIssuesChannel,
                        applicationContext.getString(R.string.websocket_notification_issues),
                        NotificationManager.IMPORTANCE_DEFAULT
                    )
                    notificationManager.createNotificationChannel(restrictedNotificationChannel)
                }
            }
            val restrictedNotification = NotificationCompat.Builder(applicationContext, websocketIssuesChannel)
                .setSmallIcon(R.drawable.ic_stat_ic_notification)
                .setContentTitle(applicationContext.getString(R.string.websocket_restricted_title))
                .setContentText(applicationContext.getString(R.string.websocket_restricted_fix))
                .setContentIntent(settingPendingIntent)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .build()
            notificationManager.notify(NOTIFICATION_RESTRICTED_ID, restrictedNotification)
            false
        }
    }
}
