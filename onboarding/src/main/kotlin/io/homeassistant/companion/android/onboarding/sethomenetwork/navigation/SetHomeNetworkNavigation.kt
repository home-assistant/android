package io.homeassistant.companion.android.onboarding.sethomenetwork.navigation

import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import io.homeassistant.companion.android.onboarding.sethomenetwork.SetHomeNetworkScreen
import kotlinx.serialization.Serializable

@Serializable
internal data class SetHomeNetworkRoute(val serverId: Int)

internal fun NavController.navigateToSetHomeNetworkRoute(serverId: Int, navOptions: NavOptions? = null) {
    navigate(SetHomeNetworkRoute(serverId), navOptions)
}

internal fun NavGraphBuilder.setHomeNetworkScreen(onGotoNextScreen: () -> Unit, onHelpClick: () -> Unit) {
    composable<SetHomeNetworkRoute> {
        SetHomeNetworkScreen(
            onGotoNextScreen = onGotoNextScreen,
            onHelpClick = onHelpClick,
            viewModel = hiltViewModel(),
        )
    }
}
