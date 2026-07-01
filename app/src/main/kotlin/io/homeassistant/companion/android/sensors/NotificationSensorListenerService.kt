package io.homeassistant.companion.android.sensors

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Thin [NotificationListenerService] that forwards the framework's notification callbacks to
 * [NotificationSensorManager], which owns all the sensor logic.
 *
 * Registered in the manifest as a `BIND_NOTIFICATION_LISTENER_SERVICE`.
 */
@AndroidEntryPoint
class NotificationSensorListenerService : NotificationListenerService() {

    @Inject
    lateinit var notificationSensorManager: NotificationSensorManager

    override fun onListenerConnected() {
        super.onListenerConnected()
        notificationSensorManager.onListenerConnectionChanged(connected = true)
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        notificationSensorManager.onListenerConnectionChanged(connected = false)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        super.onNotificationPosted(sbn)
        notificationSensorManager.onNotificationPosted(sbn, activeNotifications)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        super.onNotificationRemoved(sbn)
        notificationSensorManager.onNotificationRemoved(sbn, activeNotifications)
    }
}
