package io.homeassistant.companion.android.frontend.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import io.homeassistant.companion.android.frontend.FrontendScreen
import kotlinx.serialization.Serializable

@Serializable
data object FrontendRoute

fun NavController.navigateToFrontend(navOptions: NavOptions? = null) {
    navigate(FrontendRoute, navOptions)
}

fun NavGraphBuilder.frontendScreen() {
    composable<FrontendRoute> {
        FrontendScreen()
    }
}
