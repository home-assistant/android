package io.homeassistant.companion.android.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.navOptions
import io.homeassistant.companion.android.LauncherNavigationEvent
import io.homeassistant.companion.android.LauncherViewModel
import io.homeassistant.companion.android.automotive.AutomotiveRoute
import io.homeassistant.companion.android.automotive.carAppActivity
import io.homeassistant.companion.android.automotive.navigateToCarAppActivity
import io.homeassistant.companion.android.frontend.navigation.FrontendRoute
import io.homeassistant.companion.android.frontend.navigation.frontendScreen
import io.homeassistant.companion.android.frontend.navigation.navigateToFrontend
import io.homeassistant.companion.android.onboarding.OnboardingRoute
import io.homeassistant.companion.android.onboarding.onboarding
import io.homeassistant.companion.android.onboarding.welcome.navigation.WelcomeRoute
import kotlinx.coroutines.flow.first

@Composable
fun HANavHost(
    // TODO we could move this into the viewModel
    isAutomotive: Boolean,
    navController: NavHostController,
    onShowSnackbar: suspend (message: String, action: String?) -> Boolean,
    // Retrieve the existing ViewModel created for Launcher
    launcherViewModel: LauncherViewModel = hiltViewModel(),
) {
    var startDestination by remember { mutableStateOf<Any?>(null) }

    LaunchedEffect(launcherViewModel) {
        val navigationEvent = launcherViewModel.navigationEventsFlow.first()
        startDestination = when (navigationEvent) {
            is LauncherNavigationEvent.Frontend -> {
                if (isAutomotive) {
                    // TODO I have some doubt about the fact that we are not creating a new task
                    AutomotiveRoute
                } else {
                    FrontendRoute(navigationEvent.url)
                }
            }

            LauncherNavigationEvent.Onboarding -> OnboardingRoute
        }
    }

    // TODO subscribe a second time to react to new Intent coming into the Launcher

    startDestination?.let { startDestination ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
        ) {
            onboarding(
                navController,
                onShowSnackbar = onShowSnackbar,
                onOnboardingDone = {
                    // TODO handle registration of the device and register the server too
                    // TODO see if we could avoid a reference to the frontend (probably a parameter given to onboarding)
                    if (isAutomotive) {
                        // TODO Check if we start a new TASK or not
                        navController.navigateToCarAppActivity()
                    } else {
                        navController.navigateToFrontend(
                            navOptions = navOptions {
                                popUpTo<WelcomeRoute> {
                                    inclusive = true
                                }
                            },
                        )
                    }
                },
            )
            frontendScreen()
            carAppActivity()
            // TODO add navigation to deep link wear-phone-signin
            // It should navigate to the onboarding screen with a parameter that says we are onboarding a wear
        }
    }
}
