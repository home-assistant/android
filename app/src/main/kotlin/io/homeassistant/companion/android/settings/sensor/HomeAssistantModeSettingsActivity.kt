package io.homeassistant.companion.android.settings.sensor

import android.app.NotificationManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.common.sensors.DNDSensorManager
import io.homeassistant.companion.android.common.sensors.HomeAssistantMode
import io.homeassistant.companion.android.settings.SettingsActivity
import javax.inject.Inject
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Opens the settings of the Home Assistant mode sensor when the user follows the link to the app from the mode's page
 * in Android settings ([NotificationManager.ACTION_AUTOMATIC_ZEN_RULE]).
 *
 * The activity is exported for Android settings. It ignores a launch that names another mode; a launch without a mode
 * id is accepted, since some Android versions may not pass it and the screen it opens is harmless.
 */
@AndroidEntryPoint
class HomeAssistantModeSettingsActivity : ComponentActivity() {

    @Inject
    lateinit var homeAssistantMode: HomeAssistantMode

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            if (homeAssistantMode.isOwnModeLaunch(intent)) {
                startActivity(
                    SettingsActivity.newInstance(
                        this@HomeAssistantModeSettingsActivity,
                        SettingsActivity.Deeplink.Sensor(DNDSensorManager.homeAssistantModeSensor.id),
                    ),
                )
            } else {
                Timber.w("Ignoring a launch that is not for the Home Assistant mode")
            }
            finish()
        }
    }
}
