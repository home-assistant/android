package io.homeassistant.companion.android.onboarding

import android.content.pm.PackageManager
import androidx.compose.ui.util.fastAny
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.navOptions
import androidx.navigation.navigation
import io.homeassistant.companion.android.HAStartDestinationRoute
import io.homeassistant.companion.android.compose.navigateToUri
import io.homeassistant.companion.android.onboarding.connection.navigation.ConnectionRoute
import io.homeassistant.companion.android.onboarding.connection.navigation.connectionScreen
import io.homeassistant.companion.android.onboarding.connection.navigation.navigateToConnection
import io.homeassistant.companion.android.onboarding.localfirst.navigation.LocalFirstRoute
import io.homeassistant.companion.android.onboarding.localfirst.navigation.localFirstScreen
import io.homeassistant.companion.android.onboarding.localfirst.navigation.navigateToLocalFirst
import io.homeassistant.companion.android.onboarding.locationsharing.locationPermissions
import io.homeassistant.companion.android.onboarding.locationsharing.navigation.LocationSharingRoute
import io.homeassistant.companion.android.onboarding.locationsharing.navigation.locationSharingScreen
import io.homeassistant.companion.android.onboarding.locationsharing.navigation.navigateToLocationSharing
import io.homeassistant.companion.android.onboarding.manualserver.navigation.manualServerScreen
import io.homeassistant.companion.android.onboarding.manualserver.navigation.navigateToManualServer
import io.homeassistant.companion.android.onboarding.nameyourdevice.navigation.nameYourDeviceScreen
import io.homeassistant.companion.android.onboarding.nameyourdevice.navigation.navigateToNameYourDevice
import io.homeassistant.companion.android.onboarding.serverdiscovery.navigation.navigateToServerDiscovery
import io.homeassistant.companion.android.onboarding.serverdiscovery.navigation.serverDiscoveryScreen
import io.homeassistant.companion.android.onboarding.welcome.navigation.WelcomeRoute
import io.homeassistant.companion.android.onboarding.welcome.navigation.welcomeScreen
import kotlinx.serialization.Serializable
import timber.log.Timber

@Serializable
internal data object OnboardingRoute : HAStartDestinationRoute

internal fun NavController.navigateToOnboarding(navOptions: NavOptions? = null) {
    navigate(OnboardingRoute, navOptions)
}

/**
 * Adds the onboarding graph to the [NavGraphBuilder].It is dedicated for onboarding it can be accessed either by using the
 * route [OnboardingRoute], the start destination of this graph is [WelcomeRoute].
 *
 * TODO update docs when the rest of the graph is ready.
 */
internal fun NavGraphBuilder.onboarding(
    navController: NavController,
    onShowSnackbar: suspend (message: String, action: String?) -> Boolean,
    onOnboardingDone: () -> Unit,
) {
    navigation<OnboardingRoute>(startDestination = WelcomeRoute) {
        welcomeScreen(
            onConnectClick = navController::navigateToServerDiscovery,
            onLearnMoreClick = {
                // TODO validate the URL to use
                navController.navigateToUri("https://www.home-assistant.io")
            },
        )
        serverDiscoveryScreen(
            onConnectClick = {
                navController.navigateToConnection(it.toString())
            },
            onBackClick = navController::popBackStack,
            onManualSetupClick = navController::navigateToManualServer,
            onHelpClick = {
                // TODO validate the URL to use
                navController.navigateToUri("https://www.home-assistant.io/installation/")
            },
        )
        manualServerScreen(
            onBackClick = navController::popBackStack,
            onConnectTo = {
                navController.navigateToConnection(it.toString())
            },
            onHelpClick = {
                // TODO validate the URL to use
                navController.navigateToUri("https://www.home-assistant.io/installation/")
            },
        )
        connectionScreen(
            onAuthenticated = { url, authCode ->
                navController.navigateToNameYourDevice(
                    url = url,
                    authCode = authCode,
                    navOptions {
                        // We don't want to come back to the connection screen if we navigate to the name your device screen
                        popUpTo<ConnectionRoute> {
                            inclusive = true
                        }
                    },
                )
            },
            onShowSnackbar = onShowSnackbar,
            onBackClick = navController::popBackStack,
        )
        nameYourDeviceScreen(
            onBackClick = navController::popBackStack,
            onDeviceNamed = { serverId ->
                // TODO if external URL or cloud URL available go to Location otherwise go to local first
                navController.navigateToLocalFirst(
                    navOptions {
                        // We don't want to come back to name your device once the device
                        // is named since the auth_code has already been used.
                        // TODO might be an issue when using deeplink since WelcomeRoute might not be in the back stack entries
                        popUpTo<WelcomeRoute> {
                            inclusive = true
                        }
                    },
                )
            },
            onShowSnackbar = onShowSnackbar,
            onHelpClick = {
                // TODO validate the URL to use
                navController.navigateToUri("https://www.home-assistant.io/installation/")
            },
        )
        localFirstScreen(
            onNextClick = {
                val context = navController.context
                val shouldAskPermission = locationPermissions.fastAny {
                    ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_DENIED
                }

                if (shouldAskPermission) {
                    navController.navigateToLocationSharing(
                        navOptions {
                            popUpTo<LocalFirstRoute> { inclusive = true }
                        },
                    )
                } else {
                    // TODO maybe clean up to WelcomeRoute
                    // Cleanup stack
                    navController.popBackStack(LocalFirstRoute, true)
                    onOnboardingDone()
                }
            },
            // We don't have back button since after name your device the device is registered
        )
        // TODO Always ask location permission when
        locationSharingScreen(
            onHelpClick = {
                // TODO validate the URL to use
                navController.navigateToUri("https://www.home-assistant.io/installation/")
            },
            onGotoNextScreen = {
                Timber.e("Hello go to next screen")
                // TODO maybe clean up to WelcomeRoute
                // Cleanup stack
                navController.popBackStack(LocationSharingRoute, true)
                onOnboardingDone()
            },
            // We don't have back button since after name your device the device is registered
        )

        // locationForInternalUrl()

        // TODO ask for background permission or keep it in location sharing screen
    }
}
