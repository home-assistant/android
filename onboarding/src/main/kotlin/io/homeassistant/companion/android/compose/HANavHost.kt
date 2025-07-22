package io.homeassistant.companion.android.compose

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.navOptions
import io.homeassistant.companion.android.onboarding.connection.navigation.ConnectionRoute
import io.homeassistant.companion.android.onboarding.connection.navigation.connectionScreen
import io.homeassistant.companion.android.onboarding.connection.navigation.navigateToConnection
import io.homeassistant.companion.android.onboarding.locationsharing.navigation.locationSharingScreen
import io.homeassistant.companion.android.onboarding.locationsharing.navigation.navigateToLocationSharing
import io.homeassistant.companion.android.onboarding.manualserver.navigation.manualServerScreen
import io.homeassistant.companion.android.onboarding.manualserver.navigation.navigateToManualServer
import io.homeassistant.companion.android.onboarding.manualserver.navigation.navigateToManualServerHelp
import io.homeassistant.companion.android.onboarding.nameyourdevice.navigation.nameYourDeviceScreen
import io.homeassistant.companion.android.onboarding.nameyourdevice.navigation.navigateToNameYourDevice
import io.homeassistant.companion.android.onboarding.nameyourdevice.navigation.navigateToNameYourDeviceHelp
import io.homeassistant.companion.android.onboarding.serverdiscovery.navigation.navigateToServerDiscovery
import io.homeassistant.companion.android.onboarding.serverdiscovery.navigation.navigateToServerDiscoveryHelp
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
        welcomeScreen(
            onLearnMoreClick = navController::navigateToLearnMore,
            onConnectClick = navController::navigateToServerDiscovery,
        )
        serverDiscoveryScreen(
            onConnectClick = {
                navController.navigateToConnection(it.toString())
            },
            onHelpClick = navController::navigateToServerDiscoveryHelp,
            onBackClick = navController::popBackStack,
            onManualSetupClick = navController::navigateToManualServer,
        )
        manualServerScreen(
            onHelpClick = navController::navigateToManualServerHelp,
            onBackClick = navController::popBackStack,
            onConnectTo = {
                navController.navigateToConnection(it.toString())
            },
        )
        connectionScreen(onAuthenticated = {
            navController.navigateToNameYourDevice(
                navOptions {
                    // We don't want to come back to the connection screen if we navigate to the name your device screen
                    popUpTo<ConnectionRoute> {
                        inclusive = true
                    }
                },
            )
        })
        nameYourDeviceScreen(
            onBackClick = navController::popBackStack,
            onHelpClick = navController::navigateToNameYourDeviceHelp,
            onDeviceNamed = navController::navigateToLocationSharing,
        )
        locationSharingScreen()
    }
}
