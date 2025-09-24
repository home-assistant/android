package io.homeassistant.companion.android.onboarding.localfirst.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import io.homeassistant.companion.android.onboarding.localfirst.LocalFirstScreen
import kotlinx.serialization.Serializable

@Serializable
internal data class LocalFirstRoute(val serverId: Int, val hasPlainTextAccess: Boolean)

internal fun NavController.navigateToLocalFirst(
    serverId: Int,
    hasPlainTextAccess: Boolean,
    navOptions: NavOptions? = null,
) {
    navigate(LocalFirstRoute(serverId, hasPlainTextAccess), navOptions = navOptions)
}

internal fun NavGraphBuilder.localFirstScreen(onNextClick: (serverId: Int, hasPlainTextAccess: Boolean) -> Unit) {
    composable<LocalFirstRoute> {
        LocalFirstScreen(
            onNextClick = {
                val route = it.toRoute<LocalFirstRoute>()
                onNextClick(route.serverId, route.hasPlainTextAccess)
            },
        )
    }
}
