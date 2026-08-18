package io.homeassistant.companion.android.util

import android.app.admin.DevicePolicyManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
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

    companion object {
        fun newInstance(context: Context): Intent = Intent(context, ScreenOffAdminRequestActivity::class.java)
    }

    private val requestAdmin =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { finish() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) {
            // A recreated instance cannot count on a pending result to finish it, so leave
            // instead of lingering transparently over the task
            finish()
            return
        }

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
            // Last resort for devices without any device admin settings screen
            Timber.w(e, "Unable to open the device admin activation screen")
            Toast.makeText(this, commonR.string.screen_off_unsupported, Toast.LENGTH_LONG).show()
            finish()
        }
    }
}
