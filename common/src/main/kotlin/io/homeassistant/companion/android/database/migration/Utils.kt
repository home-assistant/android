package io.homeassistant.companion.android.database.migration

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.database.Cursor
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.getSystemService
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.util.CHANNEL_DATABASE
import io.homeassistant.companion.android.database.AppDatabase.Companion.TAG
import io.homeassistant.companion.android.database.AppDatabase.Companion.integrationRepository
import kotlinx.coroutines.runBlocking
import timber.log.Timber

private const val NOTIFICATION_ID = 45

internal fun <T> Cursor.map(transform: (Cursor) -> T): List<T> {
    return if (moveToFirst()) {
        val results = mutableListOf<T>()
        do {
            results.add(transform(this))
        } while (moveToNext())
        results
    } else {
        emptyList()
    }
}

private fun createNotificationChannel(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val notificationManager = context.getSystemService<NotificationManager>()!!

        var notificationChannel =
            notificationManager.getNotificationChannel(CHANNEL_DATABASE)
        if (notificationChannel == null) {
            notificationChannel = NotificationChannel(
                CHANNEL_DATABASE,
                TAG,
                NotificationManager.IMPORTANCE_HIGH,
            )
            notificationManager.createNotificationChannel(notificationChannel)
        }
    }
}
internal fun notifyMigrationFailed(context: Context) {
    createNotificationChannel(context)
    val notification = NotificationCompat.Builder(context, CHANNEL_DATABASE)
        .setSmallIcon(commonR.drawable.ic_stat_ic_notification)
        .setContentTitle(context.getString(commonR.string.database_migration_failed))
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .build()
    with(NotificationManagerCompat.from(context)) {
        notify(NOTIFICATION_ID, notification)
    }
    runBlocking {
        try {
            integrationRepository.fireEvent("mobile_app.migration_failed", mapOf())
            Timber.d("Event sent to Home Assistant")
        } catch (e: Exception) {
            Timber.e(e, "Unable to send event to Home Assistant")
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(
                    context,
                    commonR.string.database_event_failure,
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }
}
