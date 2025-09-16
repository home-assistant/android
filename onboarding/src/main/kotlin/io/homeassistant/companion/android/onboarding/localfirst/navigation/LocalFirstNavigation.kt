package io.homeassistant.companion.android.onboarding.localfirst.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import io.homeassistant.companion.android.onboarding.localfirst.LocalFirstScreen
import kotlinx.serialization.Serializable

@Serializable
internal data class LocalFirstRoute(val serverId: Int)

internal fun NavController.navigateToLocalFirst(serverId: Int, navOptions: NavOptions? = null) {
    navigate(LocalFirstRoute(serverId), navOptions = navOptions)
}

internal fun NavGraphBuilder.localFirstScreen(onNextClick: (serverId: Int) -> Unit) {
    composable<LocalFirstRoute> {
        LocalFirstScreen(
            onNextClick = {
                onNextClick(it.toRoute<LocalFirstRoute>().serverId)
            },
        )
    }
}
