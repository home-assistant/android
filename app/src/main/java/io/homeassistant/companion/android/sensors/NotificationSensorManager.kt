package io.homeassistant.companion.android.sensors

import android.Manifest
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import io.homeassistant.companion.android.R

class NotificationSensorManager : NotificationListenerService(), SensorManager {
    companion object {
        private const val TAG = "NotificationManager"
        private var listenerConnected = false
        val lastNotification = SensorManager.BasicSensor(
            "last_notification",
            "sensor",
            R.string.basic_sensor_name_last_notification,
            R.string.sensor_description_last_notification
        )
        val lastRemovedNotification = SensorManager.BasicSensor(
            "last_removed_notification",
            "sensor",
            R.string.basic_sensor_name_last_removed_notification,
            R.string.sensor_description_last_removed_notification
        )
        val activeNotificationCount = SensorManager.BasicSensor(
            "active_notification_count",
            "sensor",
            R.string.basic_sensor_name_active_notification_count,
            R.string.sensor_description_active_notification_count,
            unitOfMeasurement = "notifications"
        )
    }
    override val name: Int
        get() = R.string.sensor_name_last_notification
    override fun getAvailableSensors(context: Context): List<SensorManager.BasicSensor> {
        return listOf(lastNotification, lastRemovedNotification, activeNotificationCount)
    }
    override val enabledByDefault: Boolean
        get() = false

    override fun requiredPermissions(sensorId: String): Array<String> {
        return arrayOf(Manifest.permission.BIND_NOTIFICATION_LISTENER_SERVICE)
    }

    override fun checkPermission(context: Context, sensorId: String): Boolean {
        return NotificationManagerCompat
            .getEnabledListenerPackages(context)
            .contains(context.packageName)
    }

    override fun requestSensorUpdate(context: Context) {
        // Noop
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        listenerConnected = true
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        listenerConnected = false
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        super.onNotificationPosted(sbn)

        updateActiveNotificationCount()

        if (!isEnabled(applicationContext, lastNotification.id))
            return

        val allowPackages = getSetting(
            applicationContext,
            lastNotification,
            "Allow List",
            "list-apps",
            ""
        ).split(", ").filter { it.isNotBlank() }

        if (sbn.packageName == application.packageName ||
            (allowPackages.isNotEmpty() && sbn.packageName !in allowPackages)) {
            return
        }

        val attr = sbn.notification.extras.keySet()
            .map { it to sbn.notification.extras.get(it) }
            .toMap()
            .plus("package" to sbn.packageName)
            .plus("post_time" to sbn.postTime)
            .plus("is_clearable" to sbn.isClearable)
            .plus("is_ongoing" to sbn.isOngoing)

        // Attempt to use the text of the notification but fallback to package name if all else fails.
        val state = attr["android.text"] ?: attr["android.title"] ?: sbn.packageName

        onSensorUpdated(
            applicationContext,
            lastNotification,
            state.toString().take(255),
            "mdi:bell-ring",
            attr
        )

        // Need to send update!
        SensorWorker.start(applicationContext)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        super.onNotificationPosted(sbn)

        updateActiveNotificationCount()

        if (!isEnabled(applicationContext, lastRemovedNotification.id))
            return

        val allowPackages = getSetting(
            applicationContext,
            lastRemovedNotification,
            "Allow List",
            "list-apps",
            ""
        ).split(", ").filter { it.isNotBlank() }

        if (sbn.packageName == application.packageName ||
            (allowPackages.isNotEmpty() && sbn.packageName !in allowPackages)) {
            return
        }

        val attr = sbn.notification.extras.keySet()
            .map { it to sbn.notification.extras.get(it) }
            .toMap()
            .plus("package" to sbn.packageName)
            .plus("post_time" to sbn.postTime)
            .plus("is_clearable" to sbn.isClearable)
            .plus("is_ongoing" to sbn.isOngoing)

        // Attempt to use the text of the notification but fallback to package name if all else fails.
        val state = attr["android.text"] ?: attr["android.title"] ?: sbn.packageName

        onSensorUpdated(
            applicationContext,
            lastRemovedNotification,
            state.toString().take(255),
            "mdi:bell-ring",
            attr
        )

        // Need to send update!
        SensorWorker.start(applicationContext)
    }

    private fun updateActiveNotificationCount() {
        if (!isEnabled(applicationContext, activeNotificationCount.id) || !listenerConnected)
            return

        try {
            val attr: MutableMap<String, Any?> = mutableMapOf()
            for (item in activeNotifications) {
                attr += item.notification.extras.keySet()
                    .map { it + "_" + item.packageName to item.notification.extras.get(it) }
                    .toMap()
                    .plus(item.packageName + "_" + item.id + "_post_time" to item.postTime)
                    .plus(item.packageName + "_" + item.id + "_is_ongoing" to item.isOngoing)
                    .plus(item.packageName + "_" + item.id + "_is_clearable" to item.isClearable)
            }
            onSensorUpdated(
                applicationContext,
                activeNotificationCount,
                activeNotifications.size,
                "mdi:bell-ring",
                attr
            )
        } catch (e: Exception) {
            Log.e(TAG, "Unable to update active notifications", e)
        }
    }
}
