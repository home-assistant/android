package io.homeassistant.companion.android.modes

import android.app.NotificationManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.modes.HomeAssistantModeManager
import io.homeassistant.companion.android.launch.LaunchActivity
import io.homeassistant.companion.android.settings.SettingsActivity
import javax.inject.Inject
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Opens the app's setting for the Home Assistant mode when the user follows the link to the app from the mode's page
 * in Android settings ([NotificationManager.ACTION_AUTOMATIC_ZEN_RULE]), or the app's onboarding while no server is
 * registered.
 *
 * The activity is exported for Android settings, so any app can start it. That is harmless: it only opens the app's
 * settings, which stay behind the app lock, and takes nothing from the launch.
 */
@AndroidEntryPoint
class HomeAssistantModeSettingsActivity : ComponentActivity() {

    @Inject
    lateinit var homeAssistantModeManager: HomeAssistantModeManager

    @Inject
    lateinit var serverManager: ServerManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!homeAssistantModeManager.isSupported()) {
            Timber.w("Ignoring a launch for the Home Assistant mode, which this device does not support")
            finish()
            return
        }
        lifecycleScope.launch {
            startActivity(
                if (serverManager.isRegistered()) {
                    SettingsActivity.newInstance(
                        this@HomeAssistantModeSettingsActivity,
                        SettingsActivity.Deeplink.HomeAssistantMode,
                    )
                } else {
                    Intent(
                        this@HomeAssistantModeSettingsActivity,
                        LaunchActivity::class.java,
                    ).setAction(Intent.ACTION_MAIN)
                },
            )
            finish()
        }
    }
}
