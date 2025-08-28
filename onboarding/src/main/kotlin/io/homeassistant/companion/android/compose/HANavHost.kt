package io.homeassistant.companion.android.compose

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import io.homeassistant.companion.android.HAStartDestinationRoute
import io.homeassistant.companion.android.LauncherViewModel
import io.homeassistant.companion.android.frontend.navigation.frontendScreen
import io.homeassistant.companion.android.onboarding.onboarding

/**
 * Navigation host for the main application.
 *
 * This composable function sets up the navigation graph for the app,
 * determining the initial screen based on the [LauncherViewModel.navigationEventsFlow]'s state.
 * It handles navigation between the onboarding flow and the main frontend.
 *
 * @param navController The [NavHostController] for managing navigation.
 * @param onShowSnackbar A suspending function to display a snackbar.
 *                       It takes a [message] and an optional [action] label.
 *                       Returns `true` if the action was performed (if an action was provided),
 *                       `false` otherwise (e.g., dismissed).
 * @param startDestination The route to use as start of the [androidx.navigation.compose.NavHost].
 */
@Composable
internal fun HANavHost(
    navController: NavHostController,
    onShowSnackbar: suspend (message: String, action: String?) -> Boolean,
    // Retrieve the existing ViewModel created for Launcher
    startDestination: HAStartDestinationRoute?,
) {
    startDestination?.let { startDestination ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
        ) {
            onboarding(
                navController,
                onShowSnackbar = onShowSnackbar,
            )
            frontendScreen()
        }
    } // TODO Could display the loading screen just in case the splashscreen is discard too soon as a safe guard
}
