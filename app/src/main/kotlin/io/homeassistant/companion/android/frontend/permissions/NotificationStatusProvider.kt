package io.homeassistant.companion.android.frontend.permissions

/**
 * Provides the current state of notification permissions.
 */
internal fun interface NotificationStatusProvider {

    /**
     * Checks whether notifications are enabled for the app.
     *
     * @return `true` if notifications are enabled system-wide, `false` otherwise
     */
    fun areNotificationsEnabled(): Boolean
}
