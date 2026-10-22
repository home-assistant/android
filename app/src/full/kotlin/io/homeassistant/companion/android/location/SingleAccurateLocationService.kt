package io.homeassistant.companion.android.location

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
import android.graphics.Color
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.util.CHANNEL_SINGLE_ACCURATE_LOCATION
import io.homeassistant.companion.android.common.util.SdkVersion
import kotlin.time.Duration.Companion.seconds
import timber.log.Timber

private const val NOTIFICATION_ID = "SingleAccurateLocationNotification"

/**
 * Short-lived foreground service that keeps the app in the foreground while a single accurate
 * location is being obtained.
 *
 * Android throttles location requests from background apps: the fused location provider stretches
 * a 10 second request made in the background to an interval of about 10 minutes, so a forced update
 * could wait that long for its first fix. Location requests made while the app runs a location
 * foreground service are not throttled.
 *
 * The service makes no location request itself. Each caller calls [start], makes its request and calls
 * [stop] with the returned [Request] when it has its fix. The service runs until every request stopped it,
 * or for [MAX_DURATION] at most.
 */
class SingleAccurateLocationService : Service() {

    /** A request that keeps the service running, returned by [start] and released with [stop]. */
    @JvmInline
    value class Request(private val id: Long)

    companion object {
        /** Upper bound for one single accurate location request, and for this service. */
        val MAX_DURATION = 60.seconds

        private var isRunning = false

        // Requests that started the service and haven't stopped it yet. Cleared when the service stops, so
        // a request from before a restart can't stop the service a newer request is waiting on.
        private val activeRequests = mutableSetOf<Request>()
        private var lastRequestId = 0L

        private var stopRequested = false

        /**
         * Starts the service, or joins it when another request already started it.
         *
         * @return the request to pass to [stop], or `null` when Android does not allow the app to start a
         * foreground service right now; the location request then runs throttled, as it would without this
         * service.
         */
        @Synchronized
        fun start(context: Context): Request? {
            val request = Request(++lastRequestId)
            if (activeRequests.isNotEmpty()) {
                activeRequests += request
                return request
            }
            stopRequested = false
            return try {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, SingleAccurateLocationService::class.java),
                )
                activeRequests += request
                request
            } catch (e: Exception) {
                // ForegroundServiceStartNotAllowedException on Android 12+ when the app has no exemption
                Timber.w(e, "Unable to start single accurate location service")
                null
            }
        }

        /**
         * Releases [request], and stops the service once no request is left. Stopping the same request twice,
         * or a request from before the service last stopped, does nothing.
         */
        @Synchronized
        fun stop(context: Context, request: Request) {
            if (!activeRequests.remove(request) || activeRequests.isNotEmpty()) return
            if (isRunning) {
                context.stopService(Intent(context, SingleAccurateLocationService::class.java))
            } else {
                // Stopping a service before it called startForeground crashes the app, let it stop itself
                stopRequested = true
            }
        }

        @Synchronized
        private fun onStarted(): Boolean {
            isRunning = true
            val stop = stopRequested
            stopRequested = false
            return stop
        }

        @Synchronized
        private fun onStopped() {
            isRunning = false
            activeRequests.clear()
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private val stopAfterMaxDuration = Runnable {
        Timber.d("Single accurate location service reached its maximum duration, stopping")
        stopSelf()
    }

    override fun onCreate() {
        super.onCreate()

        val notificationManager = NotificationManagerCompat.from(this)
        if (SdkVersion.isAtLeast(Build.VERSION_CODES.O)) {
            notificationManager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_SINGLE_ACCURATE_LOCATION,
                    getString(commonR.string.basic_sensor_name_location_accurate),
                    NotificationManager.IMPORTANCE_LOW,
                ),
            )
        }
        val notification = NotificationCompat.Builder(this, CHANNEL_SINGLE_ACCURATE_LOCATION)
            .setSmallIcon(commonR.drawable.ic_stat_ic_notification)
            .setColor(Color.GRAY)
            .setContentTitle(getString(commonR.string.single_accurate_location_notification_title))
            .setOngoing(true)
            .setSilent(true)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()

        val type = if (SdkVersion.isAtLeast(Build.VERSION_CODES.Q)) FOREGROUND_SERVICE_TYPE_LOCATION else 0
        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID.hashCode(), notification, type)
        } catch (e: Exception) {
            Timber.w(e, "Unable to promote single accurate location service to the foreground")
            stopSelf()
            return
        }
        Timber.d("Single accurate location service started")

        if (onStarted()) {
            stopSelf()
            return
        }
        handler.postDelayed(stopAfterMaxDuration, MAX_DURATION.inWholeMilliseconds)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY

    override fun onDestroy() {
        handler.removeCallbacks(stopAfterMaxDuration)
        onStopped()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        Timber.d("Single accurate location service stopped")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
