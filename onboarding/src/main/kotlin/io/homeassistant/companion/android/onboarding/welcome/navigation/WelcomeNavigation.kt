package io.homeassistant.companion.android.onboarding.welcome.navigation

import androidx.compose.ui.platform.AndroidUriHandler
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import io.homeassistant.companion.android.onboarding.welcome.WelcomeScreen
import kotlinx.serialization.Serializable

@Serializable
data object WelcomeRoute

fun NavController.navigateToWelcome(navOptions: NavOptions) {
    navigate(route = WelcomeRoute, navOptions)
}

fun NavController.navigateToLearnMore() {
    // TODO not sure it's the best way to do this or even the place to do this
    AndroidUriHandler(context).openUri("https://home-assistant.io")
}

fun NavGraphBuilder.welcomeScreen(
    onConnectClick: () -> Unit = {},
    onLearnMoreClick: () -> Unit = {},
) {
    composable<WelcomeRoute> {
        WelcomeScreen(
            onConnectClick = onConnectClick,
            onLearnMoreClick = onLearnMoreClick,
        )
    }
}
