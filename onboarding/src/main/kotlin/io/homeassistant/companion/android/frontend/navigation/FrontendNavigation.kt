package io.homeassistant.companion.android.frontend.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import io.homeassistant.companion.android.frontend.FrontendScreen
import kotlinx.serialization.Serializable

@Serializable
data class FrontendRoute(val url: String? = null)

internal fun NavController.navigateToFrontend(url: String? = null, navOptions: NavOptions? = null) {
    navigate(FrontendRoute(url), navOptions)
}

internal fun NavGraphBuilder.frontendScreen() {
    // TODO replace by activity {} until we merge the webView within this navigation
    composable<FrontendRoute> {
        FrontendScreen(it.toRoute<FrontendRoute>().url)
    }
}
