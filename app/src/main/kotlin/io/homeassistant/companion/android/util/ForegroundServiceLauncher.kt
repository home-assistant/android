package io.homeassistant.companion.android.util

import android.app.AlarmManager
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import java.util.Calendar
import timber.log.Timber

class ForegroundServiceLauncher(private val serviceClass: Class<out Service>) {

    private var isStarting = false
    private var shouldStop = false
    private var isRunning = false
    private var restartInProcess = false

    @Synchronized
    fun startService(context: Context, block: Intent.() -> Unit = {}) {
        if (!restartInProcess && !isRunning) {
            isStarting = true
            shouldStop = false
            ContextCompat.startForegroundService(context, Intent(context, serviceClass).apply { block() })
            Timber.d("Start service ${serviceClass.simpleName}")
        } else {
            if (restartInProcess) {
                Timber.w("Cannot start service ${serviceClass.simpleName}. Service currently restarting...")
            } else {
                Timber.w("Cannot start service ${serviceClass.simpleName}. Service is not running...")
            }
        }
    }

    @Synchronized
    fun stopService(context: Context) {
        if (isStarting || restartInProcess) {
            shouldStop = true
            if (restartInProcess) {
                Timber.d(
                    "Stop service ${serviceClass.simpleName}. Service currently restarting. Stopping service after it is restarted.",
                )
            } else {
                Timber.d(
                    "Stop service ${serviceClass.simpleName}. Service is currently starting. Stopping service after it is started.",
                )
            }
        } else if (isRunning) {
            context.stopService(Intent(context, serviceClass))
            Timber.d("Stop service ${serviceClass.simpleName}")
        }
    }

    @Synchronized
    fun restartService(context: Context, block: Intent.() -> Unit = {}) {
        if (!restartInProcess && !isStarting) {
            if (isRunning) {
                Timber.d("Restart service ${serviceClass.simpleName}")

                stopService(context)

                restartInProcess = true

                // Restart service in 2 seconds
                val restartIntent = Intent(context, serviceClass).apply { block() }
                val restartServicePI = PendingIntent.getService(
                    context,
                    1,
                    restartIntent,
                    PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE,
                )

                val alarmManager: AlarmManager = context.getSystemService()!!
                val calendar: Calendar = Calendar.getInstance()
                calendar.timeInMillis = System.currentTimeMillis()
                calendar.add(Calendar.SECOND, 2)
                alarmManager.set(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, restartServicePI)
            } else {
                Timber.d("Restart service ${serviceClass.simpleName}. Service was not running.")
                startService(context, block)
            }
        } else {
            if (restartInProcess) {
                Timber.w("Cannot restart service ${serviceClass.simpleName}. Service currently restarting...")
            } else {
                Timber.w("Cannot restart service ${serviceClass.simpleName}. Service is currently starting...")
            }
        }
    }

    @Synchronized
    fun isRunning(): Boolean {
        val running = isRunning && !restartInProcess

        Timber.d("Check if service ${serviceClass.simpleName} is running. Service running = $running")

        return running
    }

    @Synchronized
    fun onServiceDestroy(service: Service) {
        Timber.d("Service ${serviceClass.simpleName} was destroyed. Stop service")
        ServiceCompat.stopForeground(service, ServiceCompat.STOP_FOREGROUND_REMOVE)
        isRunning = false
    }

    @Synchronized
    fun onServiceCreated(service: Service, id: Int, notification: Notification, foregroundServiceType: Int) {
        // Make sure to call the startForeground method as fast as possible
        ServiceCompat.startForeground(service, id, notification, foregroundServiceType)
        isStarting = false
        isRunning = true
        restartInProcess = false

        Timber.d("Service ${serviceClass.simpleName} was created. Start service")

        if (shouldStop) {
            Timber.d(
                "Service ${serviceClass.simpleName} should be stopped after. Service will be stopped after is started...",
            )
            shouldStop = false
            service.stopSelf()
        }
    }
}
