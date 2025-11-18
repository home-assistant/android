package io.homeassistant.companion.android.compose

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import io.homeassistant.companion.android.HAStartDestinationRoute
import io.homeassistant.companion.android.automotive.navigation.carAppActivity
import io.homeassistant.companion.android.automotive.navigation.navigateToCarAppActivity
import io.homeassistant.companion.android.common.util.isAutomotive
import io.homeassistant.companion.android.frontend.navigation.frontendScreen
import io.homeassistant.companion.android.frontend.navigation.navigateToFrontend
import io.homeassistant.companion.android.loading.LoadingScreen
import io.homeassistant.companion.android.loading.navigation.LoadingRoute
import io.homeassistant.companion.android.loading.navigation.loadingScreen
import io.homeassistant.companion.android.onboarding.OnboardingRoute
import io.homeassistant.companion.android.onboarding.WearOnboardingRoute
import io.homeassistant.companion.android.onboarding.onboarding
import io.homeassistant.companion.android.onboarding.wearOnboarding

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
                        // TODO Use OnboardApp contract or similar to avoid using const
                        //  OnboardApp should accept null for TLSCients
                        activity?.setResult(
                            Activity.RESULT_OK,
                            Intent().apply {
                                putExtra("URL", serverUrl)
                                putExtra("AuthCode", authCode)
                                putExtra("DeviceName", deviceName)
                                putExtra("TLSClientCertificateUri", certUri?.toString() ?: "")
                                putExtra("TLSClientCertificatePassword", certPassword ?: "")
                            },
                        )
                        activity?.finish()
                    },
                    urlToOnboard = startDestination.urlToOnboard,
                    wearNameToOnboard = startDestination.wearName,
                )
            }
            frontendScreen(navController)
            if (isAutomotive) {
                carAppActivity(navController)
            }
        }
    } ?: LoadingScreen()
}
