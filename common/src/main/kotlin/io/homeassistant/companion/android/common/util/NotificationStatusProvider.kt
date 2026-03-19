package io.homeassistant.companion.android.common.util

/**
 * Provides the current state of notification permissions.
 */
fun interface NotificationStatusProvider {

    /**
     * Checks whether notifications are enabled for the app.
     *
     * @return `true` if notifications are enabled system-wide, `false` otherwise
     */
    fun areNotificationsEnabled(): Boolean
}
