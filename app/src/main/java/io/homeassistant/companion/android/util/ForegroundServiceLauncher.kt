package io.homeassistant.companion.android.util

import android.app.AlarmManager
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import java.util.Calendar

class ForegroundServiceLauncher(private val serviceClass: Class<out Service>) {

    companion object {
        internal const val TAG = "ForegrndServiceLauncher"
    }

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
            Log.d(TAG, "Start service ${serviceClass.simpleName}")
        } else {
            if (restartInProcess) {
                Log.w(TAG, "Cannot start service ${serviceClass.simpleName}. Service currently restarting...")
            } else {
                Log.w(TAG, "Cannot start service ${serviceClass.simpleName}. Service is not running...")
            }
        }
    }

    @Synchronized
    fun stopService(context: Context) {
        if (isStarting || restartInProcess) {
            shouldStop = true
            if (restartInProcess) {
                Log.d(TAG, "Stop service ${serviceClass.simpleName}. Service currently restarting. Stopping service after it is restarted.")
            } else {
                Log.d(TAG, "Stop service ${serviceClass.simpleName}. Service is currently starting. Stopping service after it is started.")
            }
        } else if (isRunning) {
            context.stopService(Intent(context, serviceClass))
            Log.d(TAG, "Stop service ${serviceClass.simpleName}")
        }
    }

    @Synchronized
    fun restartService(context: Context, block: Intent.() -> Unit = {}) {
        if (!restartInProcess && !isStarting) {
            if (isRunning) {
                Log.d(TAG, "Restart service ${serviceClass.simpleName}")

                stopService(context)

                restartInProcess = true

                // Restart service in 2 seconds
                val restartIntent = Intent(context, serviceClass).apply { block() }
                val restartServicePI = PendingIntent.getService(context, 1, restartIntent, PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE)

                val alarmManager: AlarmManager = context.getSystemService()!!
                val calendar: Calendar = Calendar.getInstance()
                calendar.timeInMillis = System.currentTimeMillis()
                calendar.add(Calendar.SECOND, 2)
                alarmManager.set(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, restartServicePI)
            } else {
                Log.d(TAG, "Restart service ${serviceClass.simpleName}. Service was not running.")
                startService(context, block)
            }
        } else {
            if (restartInProcess) {
                Log.w(TAG, "Cannot restart service ${serviceClass.simpleName}. Service currently restarting...")
            } else {
                Log.w(TAG, "Cannot restart service ${serviceClass.simpleName}. Service is currently starting...")
            }
        }
    }

    @Synchronized
    fun isRunning(): Boolean {
        val running = isRunning && !restartInProcess

        Log.d(TAG, "Check if service ${serviceClass.simpleName} is running. Service running = $running")

        return running
    }

    @Synchronized
    fun onServiceDestroy(service: Service) {
        Log.d(TAG, "Service ${serviceClass.simpleName} was destroyed. Stop service")
        ServiceCompat.stopForeground(service, ServiceCompat.STOP_FOREGROUND_REMOVE)
        isRunning = false
    }

    @Synchronized
    fun onServiceCreated(service: Service, id: Int, notification: Notification) {
        // Make sure to call the startForeground method as fast as possible
        service.startForeground(id, notification)
        isStarting = false
        isRunning = true
        restartInProcess = false

        Log.d(TAG, "Service ${serviceClass.simpleName} was created. Start service")

        if (shouldStop) {
            Log.d(TAG, "Service ${serviceClass.simpleName} should be stopped after. Service will be stopped after is started...")
            shouldStop = false
            service.stopSelf()
        }
    }
}
