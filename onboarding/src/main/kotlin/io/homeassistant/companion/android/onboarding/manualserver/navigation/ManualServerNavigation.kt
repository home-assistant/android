package io.homeassistant.companion.android.onboarding.manualserver.navigation

import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import io.homeassistant.companion.android.onboarding.manualserver.ManualServerScreen
import java.net.URL
import kotlinx.serialization.Serializable

@Serializable
internal data object ManualServerRoute

internal fun NavController.navigateToManualServer(navOptions: NavOptions? = null) {
    navigate(ManualServerRoute, navOptions = navOptions)
}

internal fun NavGraphBuilder.manualServerScreen(
    onBackClick: () -> Unit,
    onConnectTo: (URL) -> Unit,
    onHelpClick: () -> Unit,
) {
    composable<ManualServerRoute> {
        ManualServerScreen(
            onBackClick = onBackClick,
            onConnectTo = onConnectTo,
            onHelpClick = onHelpClick,
            viewModel = hiltViewModel(),
        )
    }
}
