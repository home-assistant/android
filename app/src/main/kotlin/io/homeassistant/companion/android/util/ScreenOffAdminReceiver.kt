package io.homeassistant.companion.android.util

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** Device admin holding the force lock policy so [ScreenOffHelper] can cut the display power. */
@AndroidEntryPoint
class ScreenOffAdminReceiver : DeviceAdminReceiver() {

    @Inject
    lateinit var screenOffHelper: ScreenOffHelper

    // Without this the wake lock held while the screen was off would never be released
    override fun onDisabled(context: Context, intent: Intent) {
        screenOffHelper.turnScreenOn()
    }
}
