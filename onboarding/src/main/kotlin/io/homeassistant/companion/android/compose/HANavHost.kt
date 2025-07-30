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
import io.homeassistant.companion.android.LauncherNavigationEvent
import io.homeassistant.companion.android.LauncherViewModel
import io.homeassistant.companion.android.frontend.navigation.FrontendRoute
import io.homeassistant.companion.android.frontend.navigation.frontendScreen
import io.homeassistant.companion.android.onboarding.OnboardingRoute
import io.homeassistant.companion.android.onboarding.onboarding
import kotlinx.coroutines.flow.first

@Composable
fun HANavHost(
    navController: NavHostController,
    onShowSnackbar: suspend (message: String, action: String?) -> Boolean,
    // Retrieve the existing ViewModel created for Launcher
    launcherViewModel: LauncherViewModel = hiltViewModel(),
) {
    var startDestination by remember { mutableStateOf<Any?>(null) }

    LaunchedEffect(launcherViewModel) {
        val navigationEvent = launcherViewModel.navigationEventsFlow.first()
        startDestination = when (navigationEvent) {
            LauncherNavigationEvent.Frontend -> FrontendRoute
            LauncherNavigationEvent.Onboarding -> OnboardingRoute
        }
    }

    startDestination?.let { startDestination ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
        ) {
            onboarding(navController, onShowSnackbar)
            frontendScreen()
        }
    }
}
