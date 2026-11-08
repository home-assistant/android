package io.homeassistant.companion.android.notifications.callscreen

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.common.R as commonR
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Keeps the process alive and the microphone usable while a call is connected. It stops itself as
 * soon as [CallScreenManager] has no active call.
 */
@AndroidEntryPoint
internal class CallScreenService : Service() {

    @Inject
    lateinit var callScreenManager: CallScreenManager

    @Inject
    lateinit var notifications: CallScreenNotifications

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = callScreenManager.buildOngoingNotification()
        if (notification == null) {
            // Going foreground is mandatory after startForegroundService, even if the call is already gone
            ServiceCompat.startForeground(
                this,
                ONGOING_NOTIFICATION_ID,
                placeholderNotification(),
                foregroundType(micAvailable = false),
            )
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        val micAvailable = callScreenManager.activeCall.value?.micAvailable == true
        ServiceCompat.startForeground(this, ONGOING_NOTIFICATION_ID, notification, foregroundType(micAvailable))

        serviceScope.launch {
            callScreenManager.activeCall.first { it == null }
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun placeholderNotification(): Notification =
        NotificationCompat.Builder(this, notifications.ensureStatusChannel())
            .setSmallIcon(commonR.drawable.ic_stat_ic_notification)
            .setContentTitle(getString(commonR.string.app_name))
            .build()

    private fun foregroundType(micAvailable: Boolean) = if (micAvailable) {
        ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
    } else {
        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
    }
}
