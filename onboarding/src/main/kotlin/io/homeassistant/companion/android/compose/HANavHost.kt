package io.homeassistant.companion.android.compose

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import io.homeassistant.companion.android.frontend.navigation.frontendScreen
import io.homeassistant.companion.android.onboarding.OnboardingRoute
import io.homeassistant.companion.android.onboarding.onboarding

@Composable
fun HANavHost(
    state: HAAppState,
    onShowSnackbar: suspend (message: String, action: String?) -> Boolean,
    modifier: Modifier = Modifier,
) {
    val navController = state.navController
    NavHost(
        navController = navController,
        startDestination = OnboardingRoute,
    ) {
        onboarding(navController)
        // TODO Whole onboarding could be in a dedicated subgraph
        frontendScreen()
    }
}
