package io.homeassistant.companion.android.calls

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.content.ContextCompat
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.launch.LaunchActivity

internal const val CALL_NOTIFICATION_ID = 49001
private const val CALL_CHANNEL_ID = "native_calls"

internal fun callNotification(context: Context, state: NativeCallState): Notification {
    val manager = context.getSystemService(NotificationManager::class.java)
    manager.createNotificationChannel(
        NotificationChannel(
            CALL_CHANNEL_ID,
            context.getString(commonR.string.native_calls),
            NotificationManager.IMPORTANCE_HIGH,
        ),
    )
    val invitation = state.invitation
    val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    val openApp = PendingIntent.getActivity(context, 0, LaunchActivity.newInstance(context), flags)
    fun requestPermission(): PendingIntent = PendingIntent.getActivity(
        context,
        1,
        LaunchActivity.callPermissionIntent(
            context,
            invitation,
        ).setData(NativeCallService.answerIntent(context, invitation).data),
        flags,
    )
    val end = PendingIntent.getService(context, 2, NativeCallService.endIntent(context, invitation), flags)
    val answer = if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED
    ) {
        PendingIntent.getService(context, 3, NativeCallService.answerIntent(context, invitation), flags)
    } else {
        requestPermission()
    }
    val person = Person.Builder().setName(state.caller).setImportant(true).build()
    val style = if (state.phase ==
        NativeCallPhase.Ringing
    ) {
        NotificationCompat.CallStyle.forIncomingCall(person, end, answer)
    } else {
        NotificationCompat.CallStyle.forOngoingCall(person, end)
    }
    return NotificationCompat.Builder(context, CALL_CHANNEL_ID)
        .setSmallIcon(commonR.drawable.ic_stat_ic_notification).setContentTitle(state.caller)
        .setContentText(
            context.getString(
                if (state.phase ==
                    NativeCallPhase.Ringing
                ) {
                    commonR.string.native_call_incoming
                } else {
                    commonR.string.native_call_active
                },
            ),
        )
        .setStyle(style).setCategory(NotificationCompat.CATEGORY_CALL).setOngoing(true)
        .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setContentIntent(openApp).build()
}
