package io.homeassistant.companion.android.websocket

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.PowerManager
import androidx.concurrent.futures.await
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
import io.homeassistant.companion.android.common.util.CHANNEL_WEBSOCKET
import io.homeassistant.companion.android.common.util.CHANNEL_WEBSOCKET_ISSUES
import io.homeassistant.companion.android.database.settings.SettingsDao
import io.homeassistant.companion.android.database.settings.WebsocketSetting
import io.homeassistant.companion.android.notifications.MessagingManager
import io.homeassistant.companion.android.settings.SettingsActivity
import io.homeassistant.companion.android.util.hasActiveConnection
import io.homeassistant.companion.android.webview.WebViewActivity
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

class WebsocketManager(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "WebSocketManager"
        private const val SOURCE = "Websocket"
        private const val NOTIFICATION_ID = 65423
        private const val NOTIFICATION_RESTRICTED_ID = 65424
        private val DEFAULT_WEBSOCKET_SETTING = if (BuildConfig.FLAVOR ==
            "full"
        ) {
            WebsocketSetting.NEVER
        } else {
            WebsocketSetting.ALWAYS
        }

        suspend fun start(context: Context) {
            val websocketNotifications =
                PeriodicWorkRequestBuilder<WebsocketManager>(15, TimeUnit.MINUTES)
                    .build()

            val workManager = WorkManager.getInstance(context)
            val workInfo = workManager.getWorkInfosForUniqueWork(TAG).await().firstOrNull()

            if (workInfo == null || workInfo.state.isFinished || workInfo.state == WorkInfo.State.ENQUEUED) {
                workManager.enqueueUniquePeriodicWork(
                    TAG,
                    ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE,
                    websocketNotifications,
                )
            } else {
                workManager.enqueueUniquePeriodicWork(
                    TAG,
                    ExistingPeriodicWorkPolicy.KEEP,
                    websocketNotifications,
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
        Timber.d("Starting to listen to Websocket")
        val jobs = mutableMapOf<Int, Job>()
        manageServerJobs(jobs, this)

        // play ping pong to ensure we have a connection and server changes are handled.
        do {
            delay(30000)
        } while (jobs.values.any { it.isActive } && isActive && shouldWeRun() && manageServerJobs(jobs, this))

        jobs.forEach { it.value.cancel() }
        jobs.clear()

        Timber.d("Done listening to Websocket")

        return@withContext Result.success()
    }

    private suspend fun shouldWeRun(): Boolean = serverManager.defaultServers.any { shouldRunForServer(it.id) }

    private suspend fun shouldRunForServer(serverId: Int): Boolean {
        val server = serverManager.getServer(serverId) ?: return false
        val setting = settingsDao.get(serverId)?.websocketSetting ?: DEFAULT_WEBSOCKET_SETTING
        val isHome = server.connection.isInternal(requiresUrl = false)

        // Check for connectivity but not internet access, based on WorkManager's NetworkConnectedController API <26
        val powerManager = applicationContext.getSystemService<PowerManager>()!!
        val displayOff = !powerManager.isInteractive

        return when {
            (setting == WebsocketSetting.NEVER) -> false
            (!applicationContext.hasActiveConnection()) -> false
            !serverManager.isRegistered() -> false
            (displayOff && setting == WebsocketSetting.SCREEN_ON) -> false
            (!isHome && setting == WebsocketSetting.HOME_WIFI) -> false
            else -> true
        }
    }

    private suspend fun manageServerJobs(jobs: MutableMap<Int, Job>, coroutineScope: CoroutineScope): Boolean {
        val servers = serverManager.defaultServers

        // Clean up...
        jobs.filter { (serverId, _) ->
            servers.none { it.id == serverId } || !shouldRunForServer(serverId)
        }
            .forEach { (serverId, job) ->
                job.cancel()
                jobs.remove(serverId)
            }
        // Keep up existing connections...
        jobs.forEach { (serverId, _) ->
            coroutineScope.launch { serverManager.webSocketRepository(serverId).sendPing() }
        }
        // Start new connections...
        servers.filter { it.id !in jobs.keys && shouldRunForServer(it.id) }
            .forEach {
                jobs[it.id] = coroutineScope.launch { collectNotifications(it.id) }
            }

        return true // for while
    }

    private suspend fun collectNotifications(serverId: Int) {
        serverManager.webSocketRepository(serverId).getNotifications()?.collect {
            if (it.containsKey("hass_confirm_id")) {
                try {
                    serverManager.webSocketRepository(serverId).ackNotification(it["hass_confirm_id"].toString())
                } catch (e: Exception) {
                    Timber.e(e, "Unable to confirm received notification")
                }
            }
            val flattened = mutableMapOf<String, String>()
            if (it.containsKey("data")) {
                for ((key, value) in it["data"] as Map<*, *>) {
                    if (key == "actions" && value is List<*>) {
                        value.forEachIndexed { i, action ->
                            if (action is Map<*, *>) {
                                flattened["action_${i + 1}_key"] = action["action"].toString()
                                flattened["action_${i + 1}_title"] = action["title"].toString()
                                action["uri"]?.let { uri -> flattened["action_${i + 1}_uri"] = uri.toString() }
                                action["behavior"]?.let { behavior ->
                                    flattened["action_${i + 1}_behavior"] =
                                        behavior.toString()
                                }
                            }
                        }
                    } else {
                        flattened[key.toString()] = value.toString()
                    }
                }
            }
            // Message and title are in the root unlike all the others.
            listOf("message", "title").forEach { key ->
                if (it.containsKey(key)) {
                    flattened[key] = it[key].toString()
                }
            }
            serverManager.getServer(serverId)?.let { server ->
                flattened["webhook_id"] = server.connection.webhookId.toString()
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
            val notificationChannel = NotificationChannel(
                CHANNEL_WEBSOCKET,
                applicationContext.getString(R.string.websocket_setting_name),
                NotificationManager.IMPORTANCE_LOW,
            )
            notificationManager.createNotificationChannel(notificationChannel)
        }

        val intent = WebViewActivity.newInstance(applicationContext)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        intent.addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
        intent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE,
        )

        val settingIntent = SettingsActivity.newInstance(applicationContext, SettingsActivity.Deeplink.Websocket)
        settingIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        settingIntent.addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
        settingIntent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
        val settingPendingIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            settingIntent,
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_WEBSOCKET)
            .setSmallIcon(R.drawable.ic_stat_ic_notification)
            .setContentTitle(applicationContext.getString(R.string.websocket_listening))
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setGroup(CHANNEL_WEBSOCKET)
            .addAction(
                io.homeassistant.companion.android.R.drawable.ic_websocket,
                applicationContext.getString(R.string.settings),
                settingPendingIntent,
            )
            .build()
        return try {
            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING
            } else {
                0
            }
            setForeground(ForegroundInfo(NOTIFICATION_ID, notification, type))
            true
        } catch (e: IllegalStateException) {
            if (e is CancellationException) return false

            Timber.e(e, "Unable to setForeground due to restrictions")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (notificationManager.getNotificationChannel(CHANNEL_WEBSOCKET_ISSUES) == null) {
                    val restrictedNotificationChannel = NotificationChannel(
                        CHANNEL_WEBSOCKET_ISSUES,
                        applicationContext.getString(R.string.websocket_notification_issues),
                        NotificationManager.IMPORTANCE_DEFAULT,
                    )
                    notificationManager.createNotificationChannel(restrictedNotificationChannel)
                }
            }
            val restrictedNotification = NotificationCompat.Builder(applicationContext, CHANNEL_WEBSOCKET_ISSUES)
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
