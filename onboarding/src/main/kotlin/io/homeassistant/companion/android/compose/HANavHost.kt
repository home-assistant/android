package io.homeassistant.companion.android.compose

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import io.homeassistant.companion.android.HAStartDestinationRoute
import io.homeassistant.companion.android.frontend.navigation.frontendScreen
import io.homeassistant.companion.android.frontend.navigation.navigateToFrontend
import io.homeassistant.companion.android.loading.LoadingScreen
import io.homeassistant.companion.android.loading.navigation.LoadingRoute
import io.homeassistant.companion.android.loading.navigation.loadingScreen
import io.homeassistant.companion.android.onboarding.onboarding

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
                    navController.navigateToFrontend()
                    // TODO remove this finish when the frontend is not an activity anymore
                    activity?.finish()
                },
            )
            frontendScreen(navController)
        }
    } ?: LoadingScreen()
}
