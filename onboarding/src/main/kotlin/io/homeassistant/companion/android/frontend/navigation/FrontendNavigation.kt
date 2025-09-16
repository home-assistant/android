package io.homeassistant.companion.android.frontend.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import io.homeassistant.companion.android.HAStartDestinationRoute
import io.homeassistant.companion.android.frontend.FrontendScreen
import kotlinx.serialization.Serializable

@Serializable
internal data object FrontendRoute : HAStartDestinationRoute

internal fun NavController.navigateToFrontend(navOptions: NavOptions? = null) {
    navigate(FrontendRoute, navOptions)
}

internal fun NavGraphBuilder.frontendScreen() {
    composable<FrontendRoute> {
        FrontendScreen()
    }
}
