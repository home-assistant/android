package io.homeassistant.companion.android.common.util

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import androidx.annotation.VisibleForTesting
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.database.server.Server
import io.homeassistant.companion.android.util.isPubliclyAccessible
import javax.inject.Inject
import javax.inject.Singleton

private const val LOCAL_NETWORK_PERMISSION_WARN_TAG = "LocalNetworkPermissionWarning"

/**
 * Posts and dismisses the persistent system notification shown when background work cannot
 * proceed because the `ACCESS_LOCAL_NETWORK` runtime permission is missing.
 */
@VisibleForTesting
internal object LocalNetworkPermissionWarning {

    /**
     * Posts the warning notification listing the [affectedServers] whose local URLs cannot be
     * reached without the permission. Tapping the notification launches the app, which surfaces
     * the existing in-app permission request through `PermissionManager.checkLocalNetworkPermission()`.
     */
    @SuppressLint("MissingPermission")
    fun show(context: Context, affectedServers: List<Server>) {
        val notificationManager = NotificationManagerCompat.from(context)
        if (notificationManager.getActiveNotification(
                LOCAL_NETWORK_PERMISSION_WARN_TAG,
                LOCAL_NETWORK_PERMISSION_WARN_TAG.hashCode(),
            ) != null
        ) {
            return
        }

        ensureChannel(context, notificationManager)

        val names = affectedServers.joinToString { it.friendlyName }
        val message = context.getString(commonR.string.local_network_permission_message, names)

        val launchIntent = context.packageManager
            .getLaunchIntentForPackage(context.packageName)
            ?.apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
            ?: Intent()
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            launchIntent,
            PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_LOCAL_NETWORK_PERMISSION)
            .setSmallIcon(commonR.drawable.ic_stat_ic_notification)
            .setColor(Color.RED)
            .setOngoing(true)
            .setContentTitle(context.getString(commonR.string.local_network_permission_title))
            .setContentText(context.getString(commonR.string.local_network_permission_short_message))
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(false)
            .build()

        notificationManager.notify(
            LOCAL_NETWORK_PERMISSION_WARN_TAG,
            LOCAL_NETWORK_PERMISSION_WARN_TAG.hashCode(),
            notification,
        )
    }

    /** Dismisses any previously posted notification. Safe to call when nothing is posted. */
    fun cancel(context: Context) {
        NotificationManagerCompat.from(context).cancel(
            tag = LOCAL_NETWORK_PERMISSION_WARN_TAG,
            id = LOCAL_NETWORK_PERMISSION_WARN_TAG.hashCode(),
            cancelGroup = false,
        )
    }

    private fun ensureChannel(context: Context, notificationManager: NotificationManagerCompat) {
        if (!SdkVersion.isAtLeast(Build.VERSION_CODES.O)) return
        if (notificationManager.getNotificationChannel(CHANNEL_LOCAL_NETWORK_PERMISSION) != null) return
        val channel = NotificationChannel(
            CHANNEL_LOCAL_NETWORK_PERMISSION,
            context.getString(commonR.string.local_network_permission_warn_channel),
            NotificationManager.IMPORTANCE_HIGH,
        )
        notificationManager.createNotificationChannel(channel)
    }
}

/**
 * Checks whether the app currently holds the `ACCESS_LOCAL_NETWORK` runtime permission required
 * to reach Home Assistant servers on the local network.
 *
 * Returns `true` when the caller may proceed with background network work, `false` when it must
 * abort. When `false` is returned a persistent user-visible notification has already been posted
 * via [LocalNetworkPermissionWarning]. The use case is idempotent it also dismisses any
 * previously posted notification when the permission is now granted, the OS predates API 37, or
 * no configured server uses a local URL.
 *
 * Safe to call from any dispatcher.
 */
@Singleton
class CheckLocalNetworkPermissionUseCase @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val permissionChecker: PermissionChecker,
    private val serverManager: ServerManager,
) {
    suspend operator fun invoke(): Boolean {
        if (!SdkVersion.isAtLeast(Build.VERSION_CODES.CINNAMON_BUN)) {
            return true
        }
        if (permissionChecker.hasPermission(Manifest.permission.ACCESS_LOCAL_NETWORK)) {
            LocalNetworkPermissionWarning.cancel(context)
            return true
        }
        val affected = serversWithLocalUrls()
        if (affected.isEmpty()) {
            LocalNetworkPermissionWarning.cancel(context)
            return true
        }
        LocalNetworkPermissionWarning.show(context, affected)
        return false
    }

    private suspend fun serversWithLocalUrls(): List<Server> = serverManager.servers().filter { server ->
        server.connection.httpUrls.any {
            !it.isPubliclyAccessible()
        }
    }
}
