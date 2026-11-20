package io.homeassistant.companion.android.modes

import android.app.NotificationManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.common.modes.HomeAssistantMode
import io.homeassistant.companion.android.settings.SettingsActivity
import javax.inject.Inject
import timber.log.Timber

/**
 * Opens the app's setting for the Home Assistant mode when the user follows the link to the app from the mode's page
 * in Android settings ([NotificationManager.ACTION_AUTOMATIC_ZEN_RULE]).
 *
 * The activity is exported for Android settings, so any app can start it. That is harmless: it only opens the app's
 * settings, which stay behind the app lock, and takes nothing from the launch.
 */
@AndroidEntryPoint
class HomeAssistantModeSettingsActivity : ComponentActivity() {

    @Inject
    lateinit var homeAssistantMode: HomeAssistantMode

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (homeAssistantMode.isSupported()) {
            startActivity(SettingsActivity.newInstance(this, SettingsActivity.Deeplink.HomeAssistantMode))
        } else {
            Timber.w("Ignoring a launch for the Home Assistant mode, which this device does not support")
        }
        finish()
    }
}
