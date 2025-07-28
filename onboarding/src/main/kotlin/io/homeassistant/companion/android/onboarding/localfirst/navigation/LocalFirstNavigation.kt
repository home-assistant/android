package io.homeassistant.companion.android.onboarding.localfirst.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import io.homeassistant.companion.android.onboarding.localfirst.LocalFirstScreen
import kotlinx.serialization.Serializable

@Serializable
internal data object LocalFirstRoute

internal fun NavController.navigateToLocalFirst(navOptions: NavOptions? = null) {
    navigate(LocalFirstRoute, navOptions = navOptions)
}

internal fun NavGraphBuilder.localFirstScreen(onBackClick: () -> Unit, onNextClick: () -> Unit) {
    composable<LocalFirstRoute> {
        LocalFirstScreen(
            onBackClick = onBackClick,
            onNextClick = onNextClick,
        )
    }
}
