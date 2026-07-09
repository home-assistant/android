package io.homeassistant.companion.android.sensors

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Thin [NotificationListenerService] that forwards the framework's notification callbacks to
 * [NotificationListenerSensorManager], which owns all the sensor logic.
 *
 * Registered in the manifest as a `BIND_NOTIFICATION_LISTENER_SERVICE`.
 *
 * The class name must stay `NotificationSensorManager` for backward compatibility: Android ties the
 * user's granted notification-listener access to this service's component name, so renaming it would
 * silently revoke access for existing users, forcing them to grant it again.
 */
@AndroidEntryPoint
class NotificationSensorManager : NotificationListenerService() {

    @Inject
    lateinit var notificationListenerSensorManager: NotificationListenerSensorManager

    override fun onListenerConnected() {
        super.onListenerConnected()
        notificationListenerSensorManager.onListenerConnectionChanged(connected = true)
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        notificationListenerSensorManager.onListenerConnectionChanged(connected = false)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        super.onNotificationPosted(sbn)
        notificationListenerSensorManager.onNotificationPosted(sbn, activeNotifications)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        super.onNotificationRemoved(sbn)
        notificationListenerSensorManager.onNotificationRemoved(sbn, activeNotifications)
    }
}
