package io.homeassistant.companion.android.util.compose

import android.app.Activity
import android.net.Uri
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import io.homeassistant.companion.android.automotive.navigation.carAppActivity
import io.homeassistant.companion.android.automotive.navigation.navigateToCarAppActivity
import io.homeassistant.companion.android.common.util.DisabledLocationHandler
import io.homeassistant.companion.android.common.util.FailFast
import io.homeassistant.companion.android.common.util.isAutomotive
import io.homeassistant.companion.android.frontend.navigation.FrontendRoute
import io.homeassistant.companion.android.frontend.navigation.frontendScreen
import io.homeassistant.companion.android.frontend.navigation.navigateToFrontend
import io.homeassistant.companion.android.launch.HAStartDestinationRoute
import io.homeassistant.companion.android.launch.PipReadiness
import io.homeassistant.companion.android.loading.LoadingScreen
import io.homeassistant.companion.android.loading.navigation.LoadingRoute
import io.homeassistant.companion.android.loading.navigation.loadingScreen
import io.homeassistant.companion.android.onboarding.OnboardingRoute
import io.homeassistant.companion.android.onboarding.WearOnboardApp
import io.homeassistant.companion.android.onboarding.WearOnboardingRoute
import io.homeassistant.companion.android.onboarding.locationforsecureconnection.navigation.URL_SECURITY_LEVEL_DOCUMENTATION
import io.homeassistant.companion.android.onboarding.onboarding
import io.homeassistant.companion.android.onboarding.sethomenetwork.navigation.navigateToSetHomeNetworkRoute
import io.homeassistant.companion.android.onboarding.sethomenetwork.navigation.setHomeNetworkScreen
import io.homeassistant.companion.android.onboarding.wearOnboarding
import io.homeassistant.companion.android.settings.navigation.navigateToSettings
import io.homeassistant.companion.android.settings.server.ServerChooserFragment

/**
 * Navigation host for the main application.
 *
 * This composable function sets up the navigation graph for the whole app.
 * The [NavHost] start destination is always [LoadingRoute] until something triggers a navigation
 * to a different destination.
 *
 * @param navController The [NavHostController] for managing navigation.
 * @param startDestination The initial destination of the navigation graph. If it is null [LoadingScreen]
 *                         is displayed.
 * @param onShowSnackbar A suspending function to display a snackbar.
 *                       It takes a [message] and an optional [action] label.
 *                       Returns `true` if the action was performed (if an action was provided),
 *                       `false` otherwise (e.g., dismissed).
 */
@Composable
internal fun HANavHost(
    navController: NavHostController,
    startDestination: HAStartDestinationRoute?,
    onShowSnackbar: suspend (message: String, action: String?) -> Boolean,
    onRequestFullscreen: (Boolean) -> Unit = {},
    onPipReadinessChanged: (PipReadiness?) -> Unit = {},
) {
    val activity = LocalActivity.current
    val isAutomotive = activity?.isAutomotive() == true

    startDestination?.let {
        NavHost(
            navController = navController,
            startDestination = startDestination,
        ) {
            loadingScreen()
            onboarding(
                navController,
                onShowSnackbar = onShowSnackbar,
                onOnboardingDone = {
                    if (isAutomotive) {
                        navController.navigateToCarAppActivity()
                    } else {
                        navController.navigateToFrontend()
                    }
                },
                urlToOnboard = (startDestination as? OnboardingRoute)?.urlToOnboard,
                hideExistingServers = (startDestination as? OnboardingRoute)?.hideExistingServers == true,
                skipWelcome = (startDestination as? OnboardingRoute)?.skipWelcome == true,
                hasLocationTracking = (startDestination as? OnboardingRoute)?.hasLocationTracking == true,
            )
            if (startDestination is WearOnboardingRoute) {
                wearOnboarding(
                    navController,
                    onOnboardingDone = {
                            deviceName: String,
                            serverUrl: String,
                            authCode: String,
                            certUri: Uri?,
                            certPassword: String?,
                        ->
                        activity?.setResult(
                            Activity.RESULT_OK,
                            WearOnboardApp.Output(
                                url = serverUrl,
                                authCode = authCode,
                                deviceName = deviceName,
                                tlsClientCertificateUri = certUri?.toString(),
                                tlsClientCertificatePassword = certPassword,
                            ).toIntent(),
                        )
                        activity?.finish()
                    },
                    onShowSnackbar = onShowSnackbar,
                    urlToOnboard = startDestination.urlToOnboard,
                    wearNameToOnboard = startDestination.wearName,
                )
            }
            frontendScreen(
                navController = navController,
                onOpenExternalLink = { uri ->
                    navController.navigateToUri(uri.toString(), onShowSnackbar)
                },
                onNavigateToSettings = {
                    navController.navigateToSettings(it)
                },
                onSecurityLevelHelpClick = {
                    navController.navigateToUri(URL_SECURITY_LEVEL_DOCUMENTATION, onShowSnackbar)
                },
                onOpenLocationSettings = {
                    activity?.let { openSystemLocationSettings(it) }
                },
                onConfigureHomeNetwork = { serverId ->
                    navController.navigateToSetHomeNetworkRoute(serverId)
                },
                onShowSnackbar = onShowSnackbar,
                onShowServerSwitcher = { onServerSelected -> showServerSwitcher(activity, onServerSelected) },
                onRequestFullscreen = onRequestFullscreen,
                onPipReadinessChanged = onPipReadinessChanged,
            )
            setHomeNetworkScreen(
                onGotoNextScreen = {
                    navController.popBackStack<FrontendRoute>(inclusive = false)
                },
                onHelpClick = {
                    navController.navigateToUri(URL_SECURITY_LEVEL_DOCUMENTATION, onShowSnackbar)
                },
            )

            if (isAutomotive) {
                carAppActivity(navController)
            }
        }
    } ?: LoadingScreen()
}

/**
 * Opens the Android location settings screen.
 *
 * If location is already enabled, this is a no-op.
 * Falls back to general settings if location settings are not available.
 */
private fun openSystemLocationSettings(activity: Activity) {
    if (DisabledLocationHandler.isLocationEnabled(activity)) {
        return
    }
    activity.startActivity(DisabledLocationHandler.locationSettingsIntent(activity))
}

private fun showServerSwitcher(activity: Activity?, onServerSelected: (Int) -> Unit) {
    val fragmentActivity = activity as? FragmentActivity
    if (fragmentActivity == null) {
        FailFast.fail { "Cannot show server switcher: hosting activity is not a FragmentActivity" }
    } else {
        val fragmentManager = fragmentActivity.supportFragmentManager
        fragmentManager.setFragmentResultListener(
            ServerChooserFragment.RESULT_KEY,
            fragmentActivity,
        ) { _, bundle ->
            if (bundle.containsKey(ServerChooserFragment.RESULT_SERVER)) {
                onServerSelected(bundle.getInt(ServerChooserFragment.RESULT_SERVER))
            }
            fragmentManager.clearFragmentResultListener(ServerChooserFragment.RESULT_KEY)
        }
        ServerChooserFragment().show(fragmentManager, ServerChooserFragment.TAG)
    }
}
