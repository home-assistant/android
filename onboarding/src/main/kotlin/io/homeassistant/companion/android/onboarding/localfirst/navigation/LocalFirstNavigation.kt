package io.homeassistant.companion.android.onboarding.localfirst.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import io.homeassistant.companion.android.onboarding.localfirst.LocalFirstScreen
import kotlinx.serialization.Serializable

@Serializable
data object LocalFirstRoute

fun NavController.navigateToLocalFirst(navOptions: NavOptions? = null) {
    navigate(LocalFirstRoute, navOptions = navOptions)
}

fun NavGraphBuilder.localFirstScreen(onNextClick: () -> Unit, onBackClick: () -> Unit) {
    composable<LocalFirstRoute> {
        LocalFirstScreen(
            onNextClick = onNextClick,
            onBackClick = onBackClick,
        )
    }
}
