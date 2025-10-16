package io.homeassistant.companion.android.onboarding

import android.app.Activity
import android.content.pm.PackageManager
import android.net.Uri
import androidx.compose.ui.util.fastAny
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.navOptions
import androidx.navigation.navigation
import io.homeassistant.companion.android.HAStartDestinationRoute
import io.homeassistant.companion.android.compose.locationPermissions
import io.homeassistant.companion.android.compose.navigateToUri
import io.homeassistant.companion.android.onboarding.connection.navigation.ConnectionRoute
import io.homeassistant.companion.android.onboarding.connection.navigation.connectionScreen
import io.homeassistant.companion.android.onboarding.connection.navigation.navigateToConnection
import io.homeassistant.companion.android.onboarding.localfirst.navigation.LocalFirstRoute
import io.homeassistant.companion.android.onboarding.localfirst.navigation.localFirstScreen
import io.homeassistant.companion.android.onboarding.localfirst.navigation.navigateToLocalFirst
import io.homeassistant.companion.android.onboarding.locationforsecureconnection.navigation.LocationForSecureConnectionRoute
import io.homeassistant.companion.android.onboarding.locationforsecureconnection.navigation.locationForSecureConnectionScreen
import io.homeassistant.companion.android.onboarding.locationforsecureconnection.navigation.navigateToLocationForSecureConnection
import io.homeassistant.companion.android.onboarding.locationsharing.navigation.LocationSharingRoute
import io.homeassistant.companion.android.onboarding.locationsharing.navigation.locationSharingScreen
import io.homeassistant.companion.android.onboarding.locationsharing.navigation.navigateToLocationSharing
import io.homeassistant.companion.android.onboarding.manualserver.navigation.manualServerScreen
import io.homeassistant.companion.android.onboarding.manualserver.navigation.navigateToManualServer
import io.homeassistant.companion.android.onboarding.nameyourdevice.navigation.nameYourDeviceScreen
import io.homeassistant.companion.android.onboarding.nameyourdevice.navigation.navigateToNameYourDevice
import io.homeassistant.companion.android.onboarding.nameyourweardevice.navigation.nameYourWearDeviceScreen
import io.homeassistant.companion.android.onboarding.nameyourweardevice.navigation.navigateToNameYourWearDevice
import io.homeassistant.companion.android.onboarding.serverdiscovery.navigation.ServerDiscoveryMode
import io.homeassistant.companion.android.onboarding.serverdiscovery.navigation.ServerDiscoveryRoute
import io.homeassistant.companion.android.onboarding.serverdiscovery.navigation.navigateToServerDiscovery
import io.homeassistant.companion.android.onboarding.serverdiscovery.navigation.serverDiscoveryScreen
import io.homeassistant.companion.android.onboarding.sethomenetwork.navigation.navigateToSetHomeNetworkRoute
import io.homeassistant.companion.android.onboarding.sethomenetwork.navigation.setHomeNetworkScreen
import io.homeassistant.companion.android.onboarding.wearmtls.navigation.navigateToWearMTLS
import io.homeassistant.companion.android.onboarding.wearmtls.navigation.wearMTLSScreen
import io.homeassistant.companion.android.onboarding.welcome.navigation.WelcomeRoute
import io.homeassistant.companion.android.onboarding.welcome.navigation.welcomeScreen
import io.homeassistant.companion.android.util.canGoBack
import kotlinx.serialization.Serializable

/**
 * Navigation route for the main onboarding flow.
 *
 * @property urlToOnboard Optional server URL to onboard directly. If null, shows server discovery.
 * @property hideExistingServers When true, hides already registered servers from discovery results.
 * @property skipWelcome When true, skips the welcome screen and navigates directly to server discovery,
 *  or to the connection screen if [urlToOnboard] is provided.
 */
@Serializable
internal data class OnboardingRoute(
    val urlToOnboard: String? = null,
    val hideExistingServers: Boolean = false,
    val skipWelcome: Boolean = false,
) : HAStartDestinationRoute

/**
 * Navigation route for Wear OS device onboarding flow.
 *
 * @property wearName The name of the Wear device being onboarded.
 * @property urlToOnboard Optional server URL to onboard directly. If null, shows server discovery with existing servers.
 */
@Serializable
internal data class WearOnboardingRoute(val wearName: String, val urlToOnboard: String? = null) :
    HAStartDestinationRoute

internal fun NavController.navigateToOnboarding(
    urlToOnboard: String? = null,
    hideExistingServers: Boolean = false,
    navOptions: NavOptions? = null,
) {
    navigate(OnboardingRoute(urlToOnboard, hideExistingServers), navOptions)
}

/**
 * Adds the onboarding graph to the [NavGraphBuilder]. It is dedicated to onboarding and can be accessed by using the
 * route [OnboardingRoute], the start destination of this graph is [WelcomeRoute].
 *
 * TODO update docs when the rest of the graph is ready.
 */
internal fun NavGraphBuilder.onboarding(
    navController: NavController,
    onShowSnackbar: suspend (message: String, action: String?) -> Boolean,
    onOnboardingDone: () -> Unit,
    urlToOnboard: String?,
    hideExistingServers: Boolean,
    skipWelcome: Boolean,
) {
    val serverDiscoveryMode = if (hideExistingServers) {
        ServerDiscoveryMode.HIDE_EXISTING
    } else {
        ServerDiscoveryMode.NORMAL
    }

    val startDestination = when {
        !skipWelcome -> WelcomeRoute
        urlToOnboard.isNullOrEmpty() -> ServerDiscoveryRoute(serverDiscoveryMode)
        else -> ConnectionRoute(urlToOnboard)
    }

    navigation<OnboardingRoute>(startDestination = startDestination) {
        welcomeScreen(
            onConnectClick = {
                if (urlToOnboard.isNullOrEmpty()) {
                    navController.navigateToServerDiscovery(serverDiscoveryMode)
                } else {
                    navController.navigateToConnection(urlToOnboard)
                }
            },
            onLearnMoreClick = {
                // TODO validate the URL to use
                navController.navigateToUri("https://www.home-assistant.io")
            },
        )
        commonScreens(navController = navController, onShowSnackbar = onShowSnackbar)
        nameYourDeviceScreen(
            onBackClick = navController::popBackStack,
            onDeviceNamed = { serverId, hasPlainTextAccess, isPubliclyAccessible: Boolean ->
                val navOptions = navOptions {
                    // We don't want to come back to name your device once the device
                    // is named since the auth_code has already been used.
                    popUpTo(startDestination) {
                        inclusive = true
                    }
                }
                if (!isPubliclyAccessible) {
                    navController.navigateToLocalFirst(
                        serverId = serverId,
                        hasPlainTextAccess = hasPlainTextAccess,
                        navOptions,
                    )
                } else {
                    navController.navigateToLocationSharing(
                        serverId = serverId,
                        hasPlainTextAccess = hasPlainTextAccess,
                        navOptions,
                    )
                }
            },
            onShowSnackbar = onShowSnackbar,
            onHelpClick = {
                // TODO validate the URL to use
                navController.navigateToUri("https://www.home-assistant.io/installation/")
            },
        )
        localFirstScreen(
            onNextClick = { serverId, hasPlainTextAccess ->
                navController.navigateToLocationSharing(
                    serverId = serverId,
                    hasPlainTextAccess = hasPlainTextAccess,
                    navOptions {
                        popUpTo<LocalFirstRoute> { inclusive = true }
                    },
                )
            },
            // We don't have back button since after name your device the device is registered
        )
        locationSharingScreen(
            onHelpClick = {
                // TODO validate the URL to use
                navController.navigateToUri("https://www.home-assistant.io/installation/")
            },
            onGotoNextScreen = { serverId, hasPlainTextAccess ->
                val context = navController.context
                val shouldAskPermission = locationPermissions.fastAny {
                    ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_DENIED
                }

                if (hasPlainTextAccess) {
                    val navOptions = navOptions {
                        popUpTo<LocationSharingRoute> { inclusive = true }
                    }
                    if (shouldAskPermission) {
                        navController.navigateToLocationForSecureConnection(serverId = serverId, navOptions)
                    } else {
                        navController.navigateToSetHomeNetworkRoute(serverId = serverId, navOptions)
                    }
                } else {
                    onOnboardingDone()
                }
            },
            // We don't have back button since after name your device the device is registered
        )
        locationForSecureConnectionScreen(
            onHelpClick = {
                // TODO validate the URL to use
                navController.navigateToUri("https://www.home-assistant.io/installation/")
            },
            onGotoNextScreen = { allowInsecureConnection, serverId ->
                if (allowInsecureConnection) {
                    onOnboardingDone()
                } else {
                    navController.navigateToSetHomeNetworkRoute(
                        serverId = serverId,
                        navOptions {
                            popUpTo<LocationForSecureConnectionRoute> { inclusive = true }
                        },
                    )
                }
            },
            onShowSnackbar = onShowSnackbar,
            // We don't have back button since after name your device the device is registered
        )

        setHomeNetworkScreen(
            onHelpClick = {
                // TODO validate the URL to use
                navController.navigateToUri("https://www.home-assistant.io/installation/")
            },
            onGotoNextScreen = {
                onOnboardingDone()
            },
        )
    }
}

private fun NavGraphBuilder.commonScreens(
    navController: NavController,
    onShowSnackbar: suspend (message: String, action: String?) -> Boolean,
    wearNameToOnboard: String? = null,
) {
    serverDiscoveryScreen(
        onConnectClick = {
            navController.navigateToConnection(it.toString())
        },
        onBackClick = {
            if (navController.canGoBack()) {
                navController.popBackStack()
            } else {
                // This should only happens when we open the onboarding from the settings.
                // Once we have a navigation graph for the whole app this could be dropped.
                // For more context see: https://github.com/home-assistant/android/pull/5897#pullrequestreview-3316313923
                (navController.context as? Activity)?.finish()
            }
        },
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
        onAuthenticated = { url, authCode, requiredMTLS ->
            val navOptions = navOptions {
                // We don't want to come back to the connection screen if we navigate to the name your device screen
                popUpTo<ConnectionRoute> {
                    inclusive = true
                }
            }
            if (wearNameToOnboard != null) {
                navController.navigateToNameYourWearDevice(
                    url = url,
                    authCode = authCode,
                    requiredMTLS = requiredMTLS,
                    navOptions = navOptions,
                    defaultDeviceName = wearNameToOnboard,
                )
            } else {
                navController.navigateToNameYourDevice(
                    url = url,
                    authCode = authCode,
                    navOptions = navOptions,
                )
            }
        },
        onShowSnackbar = onShowSnackbar,
        onBackClick = navController::popBackStack,
        onOpenExternalLink = {
            navController.navigateToUri(it.toString())
        },
    )
}

internal fun NavGraphBuilder.wearOnboarding(
    navController: NavController,
    onShowSnackbar: suspend (message: String, action: String?) -> Boolean,
    onOnboardingDone: (
        deviceName: String,
        serverUrl: String,
        authCode: String,
        certUri: Uri?,
        certPassword: String?,
    ) -> Unit,
    urlToOnboard: String?,
    wearNameToOnboard: String,
) {
    val startRoute = if (urlToOnboard.isNullOrEmpty()) {
        ServerDiscoveryRoute(discoveryMode = ServerDiscoveryMode.ADD_EXISTING)
    } else {
        ConnectionRoute(urlToOnboard)
    }

    navigation<WearOnboardingRoute>(startDestination = startRoute) {
        // TODO discovery should be able to add existing system
        commonScreens(
            navController = navController,
            onShowSnackbar = onShowSnackbar,
            wearNameToOnboard = wearNameToOnboard,
        )

        nameYourWearDeviceScreen(
            onBackClick = navController::popBackStack,
            onDeviceNamed = { deviceName, serverUrl, authCode, neededMTLS ->
                if (neededMTLS) {
                    navController.navigateToWearMTLS(
                        deviceName = deviceName,
                        serverUrl = serverUrl,
                        authCode = authCode,
                    )
                } else {
                    onOnboardingDone(deviceName, serverUrl, authCode, null, null)
                }
            },
            onHelpClick = {
                // TODO validate the URL to use
                navController.navigateToUri("https://www.home-assistant.io/installation/")
            },
        )

        wearMTLSScreen(
            onBackClick = navController::popBackStack,
            onHelpClick = {
                navController.navigateToUri(
                    "https://companion.home-assistant.io/docs/getting_started/#tls-client-authentication",
                )
            },
            onNext = onOnboardingDone,
        )
        // TODO probably make auth_code a value class to avoid string missmatch
    }
}
