package io.homeassistant.companion.android.onboarding.nameyourdevice.navigation

import androidx.compose.ui.platform.AndroidUriHandler
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import io.homeassistant.companion.android.onboarding.nameyourdevice.NameYourDeviceScreen
import kotlinx.serialization.Serializable

@Serializable
data object NameYourDeviceRoute

fun NavController.navigateToNameYourDevice(navOptions: NavOptions? = null) {
    navigate(route = NameYourDeviceRoute, navOptions)
}

fun NavController.navigateToNameYourDeviceHelp() {
    // TODO not sure it's the best way to do this or even the place to do this
    AndroidUriHandler(context).openUri("https://home-assistant.io")
}

fun NavGraphBuilder.nameYourDeviceScreen(onHelpClick: () -> Unit = {}, onBackClick: () -> Unit = {}) {
    composable<NameYourDeviceRoute> {
        NameYourDeviceScreen(
            onHelpClick = onHelpClick,
            onBackClick = onBackClick,
        )
    }
}
