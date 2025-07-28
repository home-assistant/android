package io.homeassistant.companion.android.compose

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.navOptions
import io.homeassistant.companion.android.frontend.navigation.frontendScreen
import io.homeassistant.companion.android.frontend.navigation.navigateToFrontend
import io.homeassistant.companion.android.onboarding.connection.navigation.ConnectionRoute
import io.homeassistant.companion.android.onboarding.connection.navigation.connectionScreen
import io.homeassistant.companion.android.onboarding.connection.navigation.navigateToConnection
import io.homeassistant.companion.android.onboarding.localfirst.navigation.localFirstScreen
import io.homeassistant.companion.android.onboarding.localfirst.navigation.navigateToLocalFirst
import io.homeassistant.companion.android.onboarding.locationsharing.navigation.locationSharingScreen
import io.homeassistant.companion.android.onboarding.locationsharing.navigation.navigateToLocationSharing
import io.homeassistant.companion.android.onboarding.locationsharing.navigation.navigateToLocationSharingHelp
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
            onConnectClick = navController::navigateToServerDiscovery,
            onLearnMoreClick = navController::navigateToLearnMore,
        )
        serverDiscoveryScreen(
            onConnectClick = {
                navController.navigateToConnection(it.toString())
            },
            onBackClick = navController::popBackStack,
            onHelpClick = navController::navigateToServerDiscoveryHelp,
            onManualSetupClick = navController::navigateToManualServer,
        )
        manualServerScreen(
            onBackClick = navController::popBackStack,
            onConnectTo = {
                navController.navigateToConnection(it.toString())
            },
            onHelpClick = navController::navigateToManualServerHelp,
        )
        connectionScreen(
            onAuthenticated = {
                navController.navigateToNameYourDevice(
                    navOptions {
                        // We don't want to come back to the connection screen if we navigate to the name your device screen
                        popUpTo<ConnectionRoute> {
                            inclusive = true
                        }
                    },
                )
            },
        )
        nameYourDeviceScreen(
            onBackClick = navController::popBackStack,
            onDeviceNamed = {
                // TODO if external URL or cloud URL available go to Location otherwise go to local first
                navController.navigateToLocalFirst()
            },
            onHelpClick = navController::navigateToNameYourDeviceHelp,
        )
        localFirstScreen(
            onNextClick = navController::navigateToLocationSharing,
            // TODO verify backstack behavior since iOS is disabling back starting from this screen since we've registered the device
            onBackClick = navController::popBackStack,
        )
        locationSharingScreen(
            onBackClick = navController::popBackStack,
            onHelpClick = navController::navigateToLocationSharingHelp,
            onGotoNextScreen = {
                navController.navigateToFrontend(
                    navOptions {
                        popUpTo<WelcomeRoute> {
                            inclusive = true
                        }
                    },
                )
            },
        )

        // TODO Whole onboarding could be in a dedicated subgraph
        frontendScreen()
    }
}
