package io.homeassistant.companion.android.onboarding.welcome.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import io.homeassistant.companion.android.onboarding.welcome.WelcomeScreen
import kotlinx.serialization.Serializable

@Serializable
internal data object WelcomeRoute

internal fun NavController.navigateToWelcome(navOptions: NavOptions? = null) {
    navigate(route = WelcomeRoute, navOptions)
}

internal fun NavGraphBuilder.welcomeScreen(onConnectClick: () -> Unit, onLearnMoreClick: () -> Unit) {
    composable<WelcomeRoute> {
        WelcomeScreen(onConnectClick = onConnectClick, onLearnMoreClick = onLearnMoreClick)
    }
}
