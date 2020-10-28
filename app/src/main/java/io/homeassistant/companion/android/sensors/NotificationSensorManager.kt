package io.homeassistant.companion.android.sensors

import android.Manifest
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat
import io.homeassistant.companion.android.R

class NotificationSensorManager : NotificationListenerService(), SensorManager {
    companion object {
        val lastNotification = SensorManager.BasicSensor(
            "last_notification",
            "sensor",
            R.string.basic_sensor_name_last_notification,
            R.string.sensor_description_last_notification
        )
    }
    override val name: Int
        get() = R.string.sensor_name_last_notification
    override val availableSensors: List<SensorManager.BasicSensor>
        get() = listOf(lastNotification)
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

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        super.onNotificationPosted(sbn)

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
}
