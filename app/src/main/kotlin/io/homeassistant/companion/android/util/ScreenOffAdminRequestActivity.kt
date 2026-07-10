package io.homeassistant.companion.android.util

import android.app.admin.DevicePolicyManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import io.homeassistant.companion.android.common.R as commonR
import timber.log.Timber

/**
 * Opens the system screen to activate [ScreenOffAdminReceiver] as device admin. Routed through
 * this transparent activity since some manufacturers, like Samsung, refuse to open that screen
 * from a new task, which is the only way the notification handling can start an activity.
 */
class ScreenOffAdminRequestActivity : ComponentActivity() {

    private val requestAdmin =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { finish() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) return

        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
            .putExtra(
                DevicePolicyManager.EXTRA_DEVICE_ADMIN,
                ComponentName(this, ScreenOffAdminReceiver::class.java),
            )
            .putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                getString(commonR.string.screen_off_admin_description),
            )
        try {
            requestAdmin.launch(intent)
        } catch (e: ActivityNotFoundException) {
            // Some devices, like Android Automotive, have no device admin settings
            Timber.w(e, "Unable to open the device admin activation screen")
            finish()
        }
    }
}
