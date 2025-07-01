package io.homeassistant.companion.android.compose

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import io.homeassistant.companion.android.onboarding.serverdiscovery.navigation.navigateToHelp
import io.homeassistant.companion.android.onboarding.serverdiscovery.navigation.navigateToServerDiscovery
import io.homeassistant.companion.android.onboarding.serverdiscovery.navigation.serverDiscoveryScreen
import io.homeassistant.companion.android.onboarding.welcome.navigation.WelcomeRoute
import io.homeassistant.companion.android.onboarding.welcome.navigation.navigateToLearnMore
import io.homeassistant.companion.android.onboarding.welcome.navigation.welcomeScreen

@Composable
fun HANavHost(
    state: HAAppState,
    onShowSnackbar: suspend (message: String, action: String?) -> Boolean,
    modifier: Modifier = Modifier,
) {
    val navController = state.navController
    NavHost(
        navController = navController,
        startDestination = WelcomeRoute,
    ) {
        welcomeScreen(onLearnMoreClick = navController::navigateToLearnMore, onConnectClick = navController::navigateToServerDiscovery)
        serverDiscoveryScreen(
            onConnectClick = {
                // TODO
            },
            onHelpClick = navController::navigateToHelp,
            onBackClick = navController::popBackStack,
            onManualSetupClick = {
                // TODO
            },
        )
    }
}
