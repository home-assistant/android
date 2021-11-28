package io.homeassistant.companion.android.location

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import io.homeassistant.companion.android.sensors.LocationSensorManager
import io.homeassistant.companion.android.util.ForegroundServiceLauncher
import kotlin.math.abs
import kotlin.math.roundToInt
import io.homeassistant.companion.android.common.R as commonR

class HighAccuracyLocationService : Service() {

    companion object {
        internal const val TAG = "HighAccLocService"

        private lateinit var notificationBuilder: NotificationCompat.Builder
        private lateinit var notification: Notification
        private lateinit var notificationManagerCompat: NotificationManagerCompat
        private var notificationId: Int = 0

        private val LAUNCHER = ForegroundServiceLauncher(HighAccuracyLocationService::class.java)

        private const val DEFAULT_UPDATE_INTERVAL_SECONDS = 5
        const val HIGH_ACCURACY_LOCATION_NOTIFICATION_ID = "HighAccuracyLocationNotification"

        @Synchronized
        fun startService(context: Context, intervalInSeconds: Int) {
            Log.d(TAG, "Try starting high accuracy location service (Interval: ${intervalInSeconds}s)...")
            LAUNCHER.startService(context) {
                putExtra("intervalInSeconds", intervalInSeconds)
            }
        }

        @Synchronized
        fun stopService(context: Context) {

            Log.d(TAG, "Try stopping high accuracy location service...")
            LAUNCHER.stopService(context)
        }

        fun restartService(context: Context, intervalInSeconds: Int) {
            Log.d(TAG, "Try restarting high accuracy location service (Interval: ${intervalInSeconds}s)...")
            LAUNCHER.restartService(context) {
                putExtra("intervalInSeconds", intervalInSeconds)
            }
        }

        fun updateNotificationAddress(context: Context, location: Location, geocodedAddress: String = "") {
            var locationReadable = geocodedAddress
            if (locationReadable.isNullOrEmpty()) {
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
            var channelID = "High accuracy location"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(channelID, context.getString(commonR.string.high_accuracy_mode_channel_name), NotificationManager.IMPORTANCE_DEFAULT)
                notificationManagerCompat.createNotificationChannel(channel)
            }

            val disableIntent = Intent(context, HighAccuracyLocationReceiver::class.java)
            disableIntent.apply {
                action = HighAccuracyLocationReceiver.HIGH_ACCURACY_LOCATION_DISABLE
            }

            val disablePendingIntent = PendingIntent.getBroadcast(context, 0, disableIntent, PendingIntent.FLAG_MUTABLE)

            notificationBuilder = NotificationCompat.Builder(context, channelID)
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

        LAUNCHER.onServiceCreated(this, notificationId, notification)

        Log.d(TAG, "High accuracy location service created -> onCreate")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        val intervalInSeconds = intent?.getIntExtra("intervalInSeconds", DEFAULT_UPDATE_INTERVAL_SECONDS) ?: DEFAULT_UPDATE_INTERVAL_SECONDS
        requestLocationUpdates(intervalInSeconds)

        Log.d(TAG, "High accuracy location service (Interval: ${intervalInSeconds}s) started -> onStartCommand")
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()

        fusedLocationProviderClient?.removeLocationUpdates(getLocationUpdateIntent())

        LAUNCHER.onServiceDestroy(this)

        // Remove notification again. Sometimes stopForeground(true) is not enough. Just to be sure
        notificationManagerCompat.cancel(notificationId)

        Log.d(TAG, "High accuracy location service stopped -> onDestroy")
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun getLocationUpdateIntent(): PendingIntent {
        val intent = Intent(this, LocationSensorManager::class.java)
        intent.action = LocationSensorManager.ACTION_PROCESS_LOCATION
        return PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
    }

    @SuppressLint("MissingPermission")
    private fun requestLocationUpdates(intervalInSeconds: Int) {
        val request = LocationRequest()

        val intervalInMS = (intervalInSeconds * 1000).toLong()
        request.interval = intervalInMS
        request.fastestInterval = intervalInMS / 2
        request.priority = LocationRequest.PRIORITY_HIGH_ACCURACY

        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)
        fusedLocationProviderClient?.requestLocationUpdates(request, getLocationUpdateIntent())
    }
}
