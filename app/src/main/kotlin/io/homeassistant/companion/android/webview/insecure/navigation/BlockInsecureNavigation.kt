package io.homeassistant.companion.android.webview.insecure.navigation

import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import io.homeassistant.companion.android.webview.insecure.BlockInsecureScreen
import io.homeassistant.companion.android.webview.insecure.BlockInsecureViewModel
import io.homeassistant.companion.android.webview.insecure.BlockInsecureViewModelFactory
import kotlinx.serialization.Serializable

@Serializable
data class BlockInsecureRoute(val serverId: Int)

fun NavController.navigateToBlockInsecure(serverId: Int, navOptions: NavOptions? = null) {
    navigate(BlockInsecureRoute(serverId), navOptions)
}

/**
 * Registers the block insecure connection screen destination.
 *
 * This screen is shown when the user is trying to connect to a server with an insecure
 * connection (HTTP) and has not configured the security level to allow it.
 *
 * @param onRetry Callback when the user taps retry after fixing the issue
 * @param onHelpClick Callback to open help documentation
 * @param onOpenSettings Callback to open app settings
 * @param onChangeSecurityLevel Callback to navigate to security level configuration
 * @param onOpenLocationSettings Callback to open Android location settings
 * @param onConfigureHomeNetwork Callback to navigate to home network configuration
 */
fun NavGraphBuilder.blockInsecureScreen(
    onRetry: () -> Unit,
    onHelpClick: () -> Unit,
    onOpenSettings: () -> Unit,
    onChangeSecurityLevel: (serverId: Int) -> Unit,
    onOpenLocationSettings: () -> Unit,
    onConfigureHomeNetwork: (serverId: Int) -> Unit,
) {
    composable<BlockInsecureRoute> { backStackEntry ->
        val viewModel: BlockInsecureViewModel = hiltViewModel(
            creationCallback = { factory: BlockInsecureViewModelFactory ->
                val route = backStackEntry.arguments?.let {
                    BlockInsecureRoute(it.getInt("serverId"))
                } ?: throw IllegalStateException("serverId is required")
                factory.create(route.serverId)
            },
        )
        val uiState = viewModel.uiState.collectAsStateWithLifecycle()

        BlockInsecureScreen(
            missingHomeSetup = uiState.value.missingHomeSetup,
            missingLocation = uiState.value.missingLocation,
            onRetry = {
                viewModel.refresh()
                onRetry()
            },
            onHelpClick = onHelpClick,
            onOpenSettings = onOpenSettings,
            onChangeSecurityLevel = {
                val serverId = backStackEntry.arguments?.getInt("serverId")
                    ?: throw IllegalStateException("serverId is required")
                onChangeSecurityLevel(serverId)
            },
            onOpenLocationSettings = onOpenLocationSettings,
            onConfigureHomeNetwork = {
                val serverId = backStackEntry.arguments?.getInt("serverId")
                    ?: throw IllegalStateException("serverId is required")
                onConfigureHomeNetwork(serverId)
            },
        )
    }
}
