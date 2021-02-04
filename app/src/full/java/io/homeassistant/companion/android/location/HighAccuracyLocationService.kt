package io.homeassistant.companion.android.location

import android.annotation.SuppressLint
import android.app.AlarmManager
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
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.sensors.LocationSensorManager
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.roundToInt

class HighAccuracyLocationService : Service() {

    companion object {
        internal const val TAG = "HighAccLocService"

        private var restartInProcess = false
        private var isRunning = false
        private lateinit var notificationBuilder: NotificationCompat.Builder

        private const val DEFAULT_UPDATE_INTERVAL_SECONDS = 5
        const val HIGH_ACCURACY_LOCATION_NOTIFICATION_ID = "HighAccuracyLocationNotification"

        fun startService(context: Context, intervalInSeconds: Int) {
            if (!isRunning && !restartInProcess) {
                val startIntent = Intent(context, HighAccuracyLocationService::class.java)
                startIntent.putExtra("intervalInSeconds", intervalInSeconds)
                ContextCompat.startForegroundService(context, startIntent)
                Log.d(TAG, "Starting high accuracy location service (Interval: ${intervalInSeconds}s)...")
            }
        }

        fun stopService(context: Context) {
            stopService(context, false)
        }

        fun restartService(context: Context, intervalInSeconds: Int) {
            if (!restartInProcess) {
                restartInProcess = true
                if (isRunning) {
                    Log.d(TAG, "Restarting high accuracy location service (Interval: ${intervalInSeconds}s)...")

                    stopService(context, true)

                    // Restart service in 2 seconds
                    val restartIntent = Intent(context, HighAccuracyLocationService::class.java)
                    restartIntent.putExtra("intervalInSeconds", intervalInSeconds)
                    val restartServicePI = PendingIntent.getService(context, 1, restartIntent, PendingIntent.FLAG_ONE_SHOT)

                    val alarmManager: AlarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                    val calendar: Calendar = Calendar.getInstance()
                    calendar.timeInMillis = System.currentTimeMillis()
                    calendar.add(Calendar.SECOND, 2)
                    alarmManager.set(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, restartServicePI)
                } else {
                    Log.d(TAG, "Restart (Service not started yet) high accuracy location service (Interval: ${intervalInSeconds}s)...")
                    startService(context, intervalInSeconds)
                }
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

        private fun stopService(context: Context, force: Boolean) {
            if (isRunning && (!restartInProcess || force)) {
                val stopIntent = Intent(context, HighAccuracyLocationService::class.java)
                context.stopService(stopIntent)
                Log.d(TAG, "Stopping high accuracy location service...")
            }
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
                (abs(latDegrees).toString() + "°" + latMinutes + "'" + latSeconds +
                        "\"" + latDegree + " " + abs(longDegrees) + "°" + longMinutes +
                        "'" + longSeconds + "\"" + lonDegrees)
            } catch (e: java.lang.Exception) {
                ("" + String.format("%8.5f", latitude) + "  " +
                        String.format("%8.5f", longitude))
            }
        }

        private fun updateNotificationContentText(context: Context, text: String) {
            if (isRunning && !restartInProcess) {
                val notificationManager = NotificationManagerCompat.from(context)
                val notificationId = HIGH_ACCURACY_LOCATION_NOTIFICATION_ID.hashCode()
                notificationBuilder
                    .setContentText(text)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                notificationManager.notify(notificationId, notificationBuilder.build())
            }
        }

        private fun createNotificationBuilder(context: Context) {
            val notificationManager = NotificationManagerCompat.from(context)

            var channelID = "High accuracy location"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(channelID, context.getString(R.string.high_accuracy_mode_channel_name), NotificationManager.IMPORTANCE_DEFAULT)
                notificationManager.createNotificationChannel(channel)
            }

            val disableIntent = Intent(context, HighAccuracyLocationReceiver::class.java)
            disableIntent.apply {
                action = HighAccuracyLocationReceiver.HIGH_ACCURACY_LOCATION_DISABLE
            }

            val disablePendingIntent = PendingIntent.getBroadcast(context, 0, disableIntent, 0)

            notificationBuilder = NotificationCompat.Builder(context, channelID)
                .setSmallIcon(R.drawable.ic_stat_ic_notification)
                .setColor(Color.GRAY)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentTitle(context.getString(R.string.high_accuracy_mode_notification_title))
                .setVisibility(NotificationCompat.VISIBILITY_SECRET) // This hides the notification from lock screen
                .setCategory(Notification.CATEGORY_SERVICE)
                .addAction(0, context.getString(R.string.disable), disablePendingIntent)
        }
    }

    private lateinit var fusedLocationProviderClient: FusedLocationProviderClient

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        restartInProcess = false
        isRunning = true
        val intervalInSeconds = intent?.getIntExtra("intervalInSeconds", DEFAULT_UPDATE_INTERVAL_SECONDS) ?: DEFAULT_UPDATE_INTERVAL_SECONDS
        requestLocationUpdates(intervalInSeconds)
        val notificationId = HIGH_ACCURACY_LOCATION_NOTIFICATION_ID.hashCode()
        createNotificationBuilder(this)
        startForeground(notificationId, notificationBuilder.build())
        Log.d(TAG, "High accuracy location service (Interval: ${intervalInSeconds}s) started")
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        fusedLocationProviderClient.removeLocationUpdates(getLocationUpdateIntent())
        stopForeground(true)
        isRunning = false
        Log.d(TAG, "High accuracy location service stopped")

        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun getLocationUpdateIntent(): PendingIntent {
        val intent = Intent(this, LocationSensorManager::class.java)
        intent.action = LocationSensorManager.ACTION_PROCESS_LOCATION
        return PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
    }

    @SuppressLint("MissingPermission")
    private fun requestLocationUpdates(intervalInSeconds: Int) {
        val request = LocationRequest()

        val intervalInMS = (intervalInSeconds * 1000).toLong()
        request.interval = intervalInMS
        request.fastestInterval = intervalInMS / 2
        request.priority = LocationRequest.PRIORITY_HIGH_ACCURACY

        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)
        fusedLocationProviderClient.requestLocationUpdates(request, getLocationUpdateIntent())
    }
}
