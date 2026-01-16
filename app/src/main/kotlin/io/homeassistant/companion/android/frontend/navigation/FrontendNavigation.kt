package io.homeassistant.companion.android.frontend.navigation

import android.net.Uri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.activity
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import io.homeassistant.companion.android.WIPFeature
import io.homeassistant.companion.android.common.data.servers.ServerManager.Companion.SERVER_ID_ACTIVE
import io.homeassistant.companion.android.frontend.FrontendScreen
import io.homeassistant.companion.android.frontend.FrontendViewModel
import io.homeassistant.companion.android.launch.HAStartDestinationRoute
import io.homeassistant.companion.android.util.getActivity
import io.homeassistant.companion.android.webview.WebViewActivity
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class FrontendActivityRoute(
    // Override the serial name to match the name in WebViewActivity
    @SerialName("server") val serverId: Int = SERVER_ID_ACTIVE,
    val path: String? = null,
)

@Serializable
internal data class FrontendRoute(val path: String? = null, val serverId: Int = SERVER_ID_ACTIVE) :
    HAStartDestinationRoute

internal fun NavController.navigateToFrontend(
    path: String? = null,
    serverId: Int = SERVER_ID_ACTIVE,
    navOptions: NavOptions? = null,
) {
    navigate(FrontendRoute(path, serverId), navOptions)
}

/**
 * Registers the frontend/webview destination for the Home Assistant app.
 *
 * When [WIPFeature.USE_FRONTEND_V2] is enabled, uses the new Compose-based [FrontendScreen].
 * Otherwise, falls back to the legacy [WebViewActivity].
 *
 * @param navController The navigation controller
 * @param onOpenExternalLink Callback to open external links (required for V2)
 * @param onNavigateToSecurityLevel Callback to navigate to security level configuration screen (required for V2)
 * @param onNavigateToInsecure Callback to navigate to insecure connection screen (required for V2)
 */
internal fun NavGraphBuilder.frontendScreen(
    navController: NavController,
    onOpenExternalLink: (Uri) -> Unit = {},
    onNavigateToSecurityLevel: (serverId: Int) -> Unit = {},
    onNavigateToInsecure: (serverId: Int) -> Unit = {},
) {
    if (WIPFeature.USE_FRONTEND_V2) {
        composable<FrontendRoute> { backStackEntry ->
            val viewModel: FrontendViewModel = hiltViewModel()

            FrontendScreen(
                onOpenExternalLink = onOpenExternalLink,
                onNavigateToSecurityLevel = onNavigateToSecurityLevel,
                onNavigateToInsecure = onNavigateToInsecure,
                viewModel = viewModel,
            )
        }
    } else {
        composable<FrontendRoute> {
            val route = it.toRoute<FrontendRoute>()
            navController.navigate(FrontendActivityRoute(route.serverId, route.path))
            navController.context.getActivity()?.finish()
        }

        activity<FrontendActivityRoute> {
            activityClass = WebViewActivity::class
        }
    }
}
