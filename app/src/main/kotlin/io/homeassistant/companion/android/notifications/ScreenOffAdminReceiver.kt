package io.homeassistant.companion.android.notifications

import android.app.admin.DeviceAdminReceiver

/**
 * Device Admin Receiver for handling screen off command.
 * This receiver is required to use DevicePolicyManager.lockNow() functionality.
 */
class ScreenOffAdminReceiver : DeviceAdminReceiver()
