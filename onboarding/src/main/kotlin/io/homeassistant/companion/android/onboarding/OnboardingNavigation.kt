package io.homeassistant.companion.android.onboarding

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.navigation
import io.homeassistant.companion.android.HAStartDestinationRoute
import io.homeassistant.companion.android.onboarding.welcome.navigation.WelcomeRoute
import io.homeassistant.companion.android.onboarding.welcome.navigation.navigateToLearnMore
import io.homeassistant.companion.android.onboarding.welcome.navigation.welcomeScreen
import kotlinx.serialization.Serializable

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
) {
    navigation<OnboardingRoute>(startDestination = WelcomeRoute) {
        welcomeScreen(
            onConnectClick = {},
            onLearnMoreClick = navController::navigateToLearnMore,
        )
    }
}
