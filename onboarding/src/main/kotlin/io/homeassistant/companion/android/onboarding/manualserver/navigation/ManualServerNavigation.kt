package io.homeassistant.companion.android.onboarding.manualserver.navigation

import androidx.compose.ui.platform.AndroidUriHandler
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import io.homeassistant.companion.android.onboarding.manualserver.ManualServerScreen
import java.net.URL
import kotlinx.serialization.Serializable

@Serializable
data object ManualServerRoute

fun NavController.navigateToManualServer(navOptions: NavOptions? = null) {
    navigate(ManualServerRoute, navOptions = navOptions)
}

fun NavController.navigateToManualServerHelp() {
    // TODO not sure it's the best way to do this or even the place to do this
    AndroidUriHandler(context).openUri("https://home-assistant.io")
}

fun NavGraphBuilder.manualServerScreen(onBackClick: () -> Unit, onConnectTo: (URL) -> Unit, onHelpClick: () -> Unit) {
    composable<ManualServerRoute> {
        ManualServerScreen(
            onBackClick = onBackClick,
            onConnectTo = onConnectTo,
            onHelpClick = onHelpClick,
            viewModel = hiltViewModel(),
        )
    }
}
