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
 * @property hasLocationTracking Whether location tracking is available (default to full flavor = true, minimal = false)
 * @property urlToOnboard Optional server URL to onboard directly. If null, shows server discovery.
 * @property hideExistingServers When true, hides already registered servers from discovery results.
 * @property skipWelcome When true, skips the welcome screen and navigates directly to server discovery,
 *  or to the connection screen if [urlToOnboard] is provided.
 */
@Serializable
internal data class OnboardingRoute(
    val hasLocationTracking: Boolean,
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

/**
 * Defines the complete onboarding navigation graph.
 *
 * This graph manages the user flow from initial welcome through server connection, device
 * registration, and optional location/security configuration. The flow adapts based on:
 * - Server accessibility (local vs public)
 * - App flavor (full with Google Play Services vs minimal FOSS)
 * - Connection security (HTTP vs HTTPS)
 * - Permission state (location access)
 *
 * ## Flow Overview
 * 1. Welcome screen (only shown if [skipWelcome] is false)
 * 2. Server discovery (only shown if [urlToOnboard] is empty)
 * 3. Connection
 * 3. Device naming and registration
 * 4. Location/security configuration (conditional)
 * 5. Home network configuration (if applicable)
 *
 * @param navController Navigation controller for managing navigation actions
 * @param onShowSnackbar Callback to display snackbar messages to the user
 * @param onOnboardingDone Callback invoked when onboarding completes successfully
 * @param urlToOnboard Optional server URL to onboard directly, bypassing server discovery
 * @param hideExistingServers When true, hides already registered servers from discovery
 * @param skipWelcome When true, skips the welcome screen and goes directly to server discovery or connection
 * @param hasLocationTracking Whether location tracking is available (default to full flavor = true, minimal = false)
 */
internal fun NavGraphBuilder.onboarding(
    navController: NavController,
    onShowSnackbar: suspend (message: String, action: String?) -> Boolean,
    onOnboardingDone: () -> Unit,
    urlToOnboard: String?,
    hideExistingServers: Boolean,
    skipWelcome: Boolean,
    hasLocationTracking: Boolean,
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
        commonScreens(navController = navController)
        nameYourDeviceScreen(
            onBackClick = navController::popBackStack,
            onDeviceNamed = { serverId, hasPlainTextAccess, isPubliclyAccessible ->
                navController.navigateAfterDeviceRegistration(
                    serverId = serverId,
                    hasPlainTextAccess = hasPlainTextAccess,
                    isPubliclyAccessible = isPubliclyAccessible,
                    hasLocationTracking = hasLocationTracking,
                    navOptions = navOptions {
                        // We don't want to come back to name your device once the device
                        // is named since the auth_code has already been used.
                        popUpTo(startDestination) {
                            inclusive = true
                        }
                    },
                    onOnboardingDone = onOnboardingDone,
                )
            },
            onShowSnackbar = onShowSnackbar,
            onHelpClick = {
                // TODO validate the URL to use
                navController.navigateToUri("https://www.home-assistant.io/installation/")
            },
        )
        localFirstScreen(
            onNextClick = { serverId, hasPlainTextAccess ->
                val navOptions = navOptions {
                    popUpTo<LocalFirstRoute> { inclusive = true }
                }
                if (hasLocationTracking) {
                    navController.navigateToLocationSharing(
                        serverId = serverId,
                        hasPlainTextAccess = hasPlainTextAccess,
                        navOptions = navOptions,
                    )
                } else {
                    navController.navigateForMinimalFlavor(
                        serverId = serverId,
                        hasPlainTextAccess = hasPlainTextAccess,
                        navOptions = navOptions,
                        onOnboardingDone = onOnboardingDone,
                    )
                }
            },
            // We don't have back button since after name your device the device is registered
        )
        locationSharingScreen(
            onHelpClick = {
                // TODO validate the URL to use
                navController.navigateToUri("https://www.home-assistant.io/installation/")
            },
            onGotoNextScreen = { serverId, hasPlainTextAccess ->
                navController.navigateForMinimalFlavor(
                    serverId = serverId,
                    hasPlainTextAccess = hasPlainTextAccess,
                    navOptions = navOptions {
                        popUpTo<LocationSharingRoute> { inclusive = true }
                    },
                    onOnboardingDone = onOnboardingDone,
                )
            },
            // We don't have back button since after name your device the device is registered
        )
        locationForSecureConnectionScreen(
            onHelpClick = {
                navController.navigateToUri(
                    "https://companion.home-assistant.io/docs/getting_started/connection-security-level",
                )
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

/**
 * Defines screens shared between normal and Wear OS onboarding flows.
 *
 * This includes:
 * - Server discovery: Find servers on the network or via manual entry
 * - Manual server entry: Direct URL input for server connection
 * - Connection: Authentication and server validation
 */
private fun NavGraphBuilder.commonScreens(navController: NavController, wearNameToOnboard: String? = null) {
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
        onBackClick = navController::popBackStack,
        onOpenExternalLink = {
            navController.navigateToUri(it.toString())
        },
    )
}

/**
 * Checks if location permissions need to be requested.
 *
 * @return `true` if any location permission is not granted, `false` if all are granted
 */
private fun NavController.shouldRequestLocationPermissions(): Boolean {
    return locationPermissions.fastAny {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_DENIED
    }
}

/**
 * Navigates to the appropriate next screen after device registration based on server configuration.
 *
 * This function encapsulates the complex decision tree for post-registration navigation,
 * considering:
 * - Whether the server is publicly accessible
 * - Whether location tracking is available (full vs minimal flavor)
 * - Whether the connection uses plain text HTTP
 * - Whether location permissions are already granted
 *
 * @param serverId The ID of the registered server
 * @param hasPlainTextAccess Whether the server connection uses HTTP (insecure)
 * @param isPubliclyAccessible Whether the server is accessible from the public internet
 * @param hasLocationTracking Whether this app flavor includes location tracking (full flavor)
 * @param navOptions Navigation options to apply when navigating
 * @param onOnboardingDone Callback to invoke when onboarding is complete
 */
private fun NavController.navigateAfterDeviceRegistration(
    serverId: Int,
    hasPlainTextAccess: Boolean,
    isPubliclyAccessible: Boolean,
    hasLocationTracking: Boolean,
    navOptions: NavOptions,
    onOnboardingDone: () -> Unit,
) {
    when {
        // Local-only servers shows local first screen
        !isPubliclyAccessible -> navigateToLocalFirst(
            serverId = serverId,
            hasPlainTextAccess = hasPlainTextAccess,
            navOptions = navOptions,
        )
        // Full flavor with location tracking: always show location sharing screen
        hasLocationTracking -> navigateToLocationSharing(
            serverId = serverId,
            hasPlainTextAccess = hasPlainTextAccess,
            navOptions = navOptions,
        )
        // Minimal flavor: handle location and security based on connection type
        else -> navigateForMinimalFlavor(
            serverId = serverId,
            hasPlainTextAccess = hasPlainTextAccess,
            navOptions = navOptions,
            onOnboardingDone = onOnboardingDone,
        )
    }
}

/**
 * Navigates to the appropriate screen for minimal flavor after location-related setup.
 *
 * For minimal flavor (without location tracking), the flow is:
 * - If HTTPS: onboarding is complete
 * - If HTTP and no location permission: ask for location to enable secure connection detection
 * - If HTTP and has location permission: configure home network
 */
private fun NavController.navigateForMinimalFlavor(
    serverId: Int,
    hasPlainTextAccess: Boolean,
    navOptions: NavOptions,
    onOnboardingDone: () -> Unit,
) {
    if (!hasPlainTextAccess) {
        // HTTPS connection: secure, onboarding complete
        onOnboardingDone()
        return
    }

    // HTTP connection: need location for secure connection detection
    if (shouldRequestLocationPermissions()) {
        navigateToLocationForSecureConnection(serverId = serverId, navOptions = navOptions)
    } else {
        navigateToSetHomeNetworkRoute(serverId = serverId, navOptions = navOptions)
    }
}

/**
 * Defines the onboarding navigation graph for Wear OS devices.
 *
 * This simplified flow is designed for pairing a Wear OS device with an existing Home Assistant
 * server. The flow includes:
 * 1. Server discovery showing existing servers (only shown if [urlToOnboard] is empty)
 * 2. Connection
 * 3. Wear device naming
 * 4. Optional mTLS certificate configuration (if required by server)
 *
 * Unlike mobile onboarding, this flow doesn't include location or home network configuration.
 *
 * @param navController Navigation controller for managing navigation actions
 * @param onOnboardingDone Callback invoked with device details when onboarding completes
 * @param urlToOnboard Optional server URL to onboard directly, bypassing server discovery
 * @param wearNameToOnboard Default name for the Wear device being onboarded
 */
internal fun NavGraphBuilder.wearOnboarding(
    navController: NavController,
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
        commonScreens(navController = navController, wearNameToOnboard = wearNameToOnboard)
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
        // TODO: Consider making auth_code a value class to prevent string parameter mismatches
    }
}
