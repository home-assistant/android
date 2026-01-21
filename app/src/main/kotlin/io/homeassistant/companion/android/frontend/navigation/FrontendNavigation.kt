package io.homeassistant.companion.android.frontend.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import io.homeassistant.companion.android.frontend.FrontendViewState
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
        composable<FrontendRoute> {
            val viewModel: FrontendViewModel = hiltViewModel()
            val viewState by viewModel.viewState.collectAsStateWithLifecycle()

            FrontendNavigationHandler(
                viewState = viewState,
                onNavigateToSecurityLevel = onNavigateToSecurityLevel,
                onNavigateToInsecure = onNavigateToInsecure,
            )

            FrontendScreen(
                onBackClick = navController::popBackStack,
                viewState = viewState,
                webViewClient = viewModel.webViewClient,
                javascriptInterface = viewModel.javascriptInterface,
                scriptsToEvaluate = viewModel.scriptsToEvaluate,
                onRetry = viewModel::onRetry,
                onOpenExternalLink = onOpenExternalLink,
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

/**
 * Handles navigation side effects based on the current [FrontendViewState].
 *
 * Triggers navigation callbacks when the view state requires navigating to other screens
 * (security level configuration or insecure connection warning).
 */
@Composable
internal fun FrontendNavigationHandler(
    viewState: FrontendViewState,
    onNavigateToSecurityLevel: (serverId: Int) -> Unit,
    onNavigateToInsecure: (serverId: Int) -> Unit,
) {
    LaunchedEffect(viewState) {
        when (viewState) {
            is FrontendViewState.SecurityLevelRequired -> {
                onNavigateToSecurityLevel(viewState.serverId)
            }
            is FrontendViewState.Insecure -> {
                onNavigateToInsecure(viewState.serverId)
            }
            else -> { /* No navigation needed */ }
        }
    }
}
