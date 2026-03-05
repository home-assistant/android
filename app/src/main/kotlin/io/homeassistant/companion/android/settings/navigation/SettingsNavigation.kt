package io.homeassistant.companion.android.settings.navigation

import androidx.navigation.NavController
import io.homeassistant.companion.android.settings.SettingsActivity

/**
 * Navigates to the [SettingsActivity], optionally deep linking to a specific settings screen.
 */
fun NavController.navigateToSettings(deeplink: SettingsActivity.Deeplink? = null) {
    context.startActivity(SettingsActivity.newInstance(context, deeplink))
}
