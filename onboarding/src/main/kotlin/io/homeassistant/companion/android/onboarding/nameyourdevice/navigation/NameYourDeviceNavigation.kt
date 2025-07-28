package io.homeassistant.companion.android.onboarding.nameyourdevice.navigation

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.AndroidUriHandler
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import io.homeassistant.companion.android.onboarding.nameyourdevice.NameYourDeviceNavigationEvent
import io.homeassistant.companion.android.onboarding.nameyourdevice.NameYourDeviceScreen
import io.homeassistant.companion.android.onboarding.nameyourdevice.NameYourDeviceViewModel
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

fun NavGraphBuilder.nameYourDeviceScreen(onBackClick: () -> Unit, onDeviceNamed: () -> Unit, onHelpClick: () -> Unit) {
    composable<NameYourDeviceRoute> {
        val viewModel: NameYourDeviceViewModel = hiltViewModel()

        LaunchedEffect(viewModel) {
            viewModel.navigationEventsFlow.collect {
                when (it) {
                    NameYourDeviceNavigationEvent.DeviceNameSaved -> onDeviceNamed()
                }
            }
        }

        NameYourDeviceScreen(
            onBackClick = onBackClick,
            onHelpClick = onHelpClick,
            viewModel = viewModel,
        )
    }
}
