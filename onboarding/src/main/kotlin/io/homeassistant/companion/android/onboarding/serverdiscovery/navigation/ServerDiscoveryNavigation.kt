package io.homeassistant.companion.android.onboarding.serverdiscovery.navigation

import androidx.compose.ui.platform.AndroidUriHandler
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import io.homeassistant.companion.android.onboarding.serverdiscovery.ServerDiscoveryScreen
import java.net.URL
import kotlinx.serialization.Serializable

@Serializable
data object ServerDiscoveryRoute

fun NavController.navigateToServerDiscovery(navOptions: NavOptions? = null) {
    navigate(route = ServerDiscoveryRoute, navOptions)
}

fun NavController.navigateToServerDiscoveryHelp() {
    // TODO not sure it's the best way to do this or even the place to do this
    AndroidUriHandler(context).openUri("https://home-assistant.io")
}

fun NavGraphBuilder.serverDiscoveryScreen(
    onConnectClick: (server: URL) -> Unit,
    onBackClick: () -> Unit,
    onHelpClick: () -> Unit,
    onManualSetupClick: () -> Unit,
) {
    composable<ServerDiscoveryRoute> {
        ServerDiscoveryScreen(
            onConnectClick = onConnectClick,
            onBackClick = onBackClick,
            onHelpClick = onHelpClick,
            onManualSetupClick = onManualSetupClick,
            viewModel = hiltViewModel(),
        )
    }
}
