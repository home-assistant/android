package io.homeassistant.companion.android.settings.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.activity
import io.homeassistant.companion.android.settings.SettingsActivity
import kotlinx.serialization.Serializable

@Serializable
internal data object SettingsRoute

internal fun NavGraphBuilder.settingsScreen() {
    activity<SettingsRoute> {
        activityClass = SettingsActivity::class
    }
}

fun NavController.navigateToSettings(navOptions: NavOptions? = null) {
    navigate(SettingsRoute, navOptions)
}
