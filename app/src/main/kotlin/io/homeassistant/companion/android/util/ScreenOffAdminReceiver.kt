package io.homeassistant.companion.android.util

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * Device admin holding only the force lock policy so [ScreenOffHelper] can cut the display power.
 * The app opens the system's activation screen for it when the screen off command is used while
 * it is not active.
 */
class ScreenOffAdminReceiver : DeviceAdminReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ScreenOffAdminEntryPoint {
        fun screenOffHelper(): ScreenOffHelper
    }

    /**
     * Turns the screen back on when the user deactivates the device admin, otherwise the wake lock
     * held while the screen is off would never be released since the screen cannot be turned off
     * anymore.
     */
    override fun onDisabled(context: Context, intent: Intent) {
        EntryPointAccessors
            .fromApplication(context.applicationContext, ScreenOffAdminEntryPoint::class.java)
            .screenOffHelper()
            .turnScreenOn()
    }
}
