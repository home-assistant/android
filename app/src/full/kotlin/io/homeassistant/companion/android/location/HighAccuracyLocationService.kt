package io.homeassistant.companion.android.location

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
import android.graphics.Color
import android.location.Location
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.util.CHANNEL_HIGH_ACCURACY
import io.homeassistant.companion.android.common.util.FailFast
import io.homeassistant.companion.android.sensors.LocationSensorManager
import io.homeassistant.companion.android.util.ForegroundServiceLauncher
import kotlin.math.abs
import kotlin.math.roundToInt
import timber.log.Timber

class HighAccuracyLocationService : Service() {

    companion object {
        private lateinit var notificationBuilder: NotificationCompat.Builder
        private lateinit var notification: Notification
        private lateinit var notificationManagerCompat: NotificationManagerCompat
        private var notificationId: Int = 0

        private val LAUNCHER = ForegroundServiceLauncher(HighAccuracyLocationService::class.java)

        private const val DEFAULT_UPDATE_INTERVAL_SECONDS = 5
        const val HIGH_ACCURACY_LOCATION_NOTIFICATION_ID = "HighAccuracyLocationNotification"

        @Synchronized
        fun startService(context: Context, intervalInSeconds: Int) {
            Timber.d("Try starting high accuracy location service (Interval: ${intervalInSeconds}s)...")
            LAUNCHER.startService(context) {
                putExtra("intervalInSeconds", intervalInSeconds)
            }
        }

        @Synchronized
        fun stopService(context: Context) {
            Timber.d("Try stopping high accuracy location service...")
            LAUNCHER.stopService(context)
        }

        fun restartService(context: Context, intervalInSeconds: Int) {
            Timber.d("Try restarting high accuracy location service (Interval: ${intervalInSeconds}s)...")
            LAUNCHER.restartService(context) {
                putExtra("intervalInSeconds", intervalInSeconds)
            }
        }

        fun updateNotificationAddress(context: Context, location: Location, geocodedAddress: String = "") {
            var locationReadable = geocodedAddress
            if (locationReadable.isEmpty()) {
                locationReadable = getFormattedLocationInDegree(location.latitude, location.longitude)
            }
            locationReadable = "$locationReadable (~${location.accuracy}m)"

            updateNotificationContentText(context, locationReadable)
        }

        private fun getFormattedLocationInDegree(latitude: Double, longitude: Double): String {
            return try {
                var latSeconds = (latitude * 3600).roundToInt()
                val latDegrees = latSeconds / 3600
                latSeconds = abs(latSeconds % 3600)
                val latMinutes = latSeconds / 60
                latSeconds %= 60
                var longSeconds = (longitude * 3600).roundToInt()
                val longDegrees = longSeconds / 3600
                longSeconds = abs(longSeconds % 3600)
                val longMinutes = longSeconds / 60
                longSeconds %= 60
                val latDegree = if (latDegrees >= 0) "N" else "S"
                val lonDegrees = if (longDegrees >= 0) "E" else "W"
                (
                    abs(latDegrees).toString() + "°" + latMinutes + "'" + latSeconds +
                        "\"" + latDegree + " " + abs(longDegrees) + "°" + longMinutes +
                        "'" + longSeconds + "\"" + lonDegrees
                    )
            } catch (e: java.lang.Exception) {
                (
                    "" + String.format("%8.5f", latitude) + "  " +
                        String.format("%8.5f", longitude)
                    )
            }
        }

        private fun updateNotificationContentText(context: Context, text: String) {
            if (LAUNCHER.isRunning()) {
                val notificationManager = NotificationManagerCompat.from(context)
                val notificationId = HIGH_ACCURACY_LOCATION_NOTIFICATION_ID.hashCode()
                notificationBuilder
                    .setContentText(text)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                notificationManager.notify(notificationId, notificationBuilder.build())
            }
        }

        private fun createNotificationBuilder(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel =
                    NotificationChannel(
                        CHANNEL_HIGH_ACCURACY,
                        context.getString(commonR.string.high_accuracy_mode_channel_name),
                        NotificationManager.IMPORTANCE_DEFAULT,
                    )
                notificationManagerCompat.createNotificationChannel(channel)
            }

            val disableIntent = Intent(context, HighAccuracyLocationReceiver::class.java)
            disableIntent.apply {
                action = HighAccuracyLocationReceiver.HIGH_ACCURACY_LOCATION_DISABLE
            }

            val disablePendingIntent = PendingIntent.getBroadcast(context, 0, disableIntent, PendingIntent.FLAG_MUTABLE)

            notificationBuilder = NotificationCompat.Builder(context, CHANNEL_HIGH_ACCURACY)
                .setSmallIcon(commonR.drawable.ic_stat_ic_notification)
                .setColor(Color.GRAY)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentTitle(context.getString(commonR.string.high_accuracy_mode_notification_title))
                .setVisibility(NotificationCompat.VISIBILITY_SECRET) // This hides the notification from lock screen
                .setCategory(Notification.CATEGORY_SERVICE)
                .addAction(0, context.getString(commonR.string.disable), disablePendingIntent)
        }
    }

    private var fusedLocationProviderClient: FusedLocationProviderClient? = null

    override fun onCreate() {
        super.onCreate()

        notificationId = HIGH_ACCURACY_LOCATION_NOTIFICATION_ID.hashCode()

        notificationManagerCompat = NotificationManagerCompat.from(this)

        // Create notification
        createNotificationBuilder(this)
        notification = notificationBuilder.build()

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) FOREGROUND_SERVICE_TYPE_LOCATION else 0
        FailFast.failOnCatch {
            // Sometimes the service cannot be started as foreground due to the app being in a state where
            // this is not allowed. We haven't identified how to avoid starting the service in this state yet.
            LAUNCHER.onServiceCreated(this, notificationId, notification, type)
        }

        Timber.d("High accuracy location service created -> onCreate")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        val intervalInSeconds =
            intent?.getIntExtra("intervalInSeconds", DEFAULT_UPDATE_INTERVAL_SECONDS) ?: DEFAULT_UPDATE_INTERVAL_SECONDS
        requestLocationUpdates(intervalInSeconds)

        Timber.d("High accuracy location service (Interval: ${intervalInSeconds}s) started -> onStartCommand")
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()

        fusedLocationProviderClient?.removeLocationUpdates(getLocationUpdateIntent())

        LAUNCHER.onServiceDestroy(this)

        // Remove notification again. Sometimes stopForeground(true) is not enough. Just to be sure
        notificationManagerCompat.cancel(notificationId)

        Timber.d("High accuracy location service stopped -> onDestroy")
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun getLocationUpdateIntent(): PendingIntent {
        val intent = Intent(this, LocationSensorManager::class.java)
        intent.action = LocationSensorManager.ACTION_PROCESS_HIGH_ACCURACY_LOCATION
        return PendingIntent.getBroadcast(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
    }

    @SuppressLint("MissingPermission")
    private fun requestLocationUpdates(intervalInSeconds: Int) {
        val intervalInMS = (intervalInSeconds * 1000).toLong()
        val request = LocationRequest.Builder(intervalInMS)
            .setMinUpdateIntervalMillis(intervalInMS / 2)
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            .build()

        fusedLocationProviderClient = try {
            LocationServices.getFusedLocationProviderClient(this)
        } catch (e: Exception) {
            Timber.e(e, "Unable to get fused location provider client")
            null
        }
        fusedLocationProviderClient?.requestLocationUpdates(request, getLocationUpdateIntent())
    }
}
