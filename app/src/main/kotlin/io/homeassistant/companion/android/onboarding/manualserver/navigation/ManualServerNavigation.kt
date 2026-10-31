package io.homeassistant.companion.android.onboarding.manualserver.navigation

import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import io.homeassistant.companion.android.onboarding.manualserver.ManualServerScreen
import io.homeassistant.companion.android.util.compose.dropUnlessResumed
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
    onHelpClick: suspend () -> Unit,
) {
    composable<ManualServerRoute> {
        ManualServerScreen(
            onBackClick = dropUnlessResumed(block = onBackClick),
            onConnectTo = dropUnlessResumed(block = onConnectTo),
            onHelpClick = onHelpClick,
            viewModel = hiltViewModel(),
        )
    }
}
