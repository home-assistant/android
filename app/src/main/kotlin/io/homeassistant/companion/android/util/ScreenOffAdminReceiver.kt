package io.homeassistant.companion.android.util

import android.app.admin.DeviceAdminReceiver

/** Device admin holding the force lock policy so [ScreenOffHelper] can cut the display power. */
class ScreenOffAdminReceiver : DeviceAdminReceiver()
