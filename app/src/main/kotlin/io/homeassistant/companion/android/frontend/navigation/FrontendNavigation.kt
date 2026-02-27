package io.homeassistant.companion.android.frontend.navigation

import android.net.Uri
import androidx.annotation.VisibleForTesting
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.activity
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import io.homeassistant.companion.android.WIPFeature
import io.homeassistant.companion.android.assist.AssistActivity
import io.homeassistant.companion.android.common.data.servers.ServerManager.Companion.SERVER_ID_ACTIVE
import io.homeassistant.companion.android.frontend.FrontendScreen
import io.homeassistant.companion.android.frontend.FrontendViewModel
import io.homeassistant.companion.android.frontend.FrontendViewState
import io.homeassistant.companion.android.launch.HAStartDestinationRoute
import io.homeassistant.companion.android.util.getActivity
import io.homeassistant.companion.android.webview.WebViewActivity
import kotlinx.coroutines.flow.SharedFlow
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
 * @param onNavigateToSettings Callback to navigate to settings
 * @param onOpenLocationSettings Callback to open location settings
 * @param onConfigureHomeNetwork Callback to configure home network (receives serverId)
 * @param onSecurityLevelHelpClick Callback when user taps help on security level screen
 * @param onShowSnackbar Callback to show snackbar messages
 */
internal fun NavGraphBuilder.frontendScreen(
    navController: NavController,
    onOpenExternalLink: suspend (Uri) -> Unit = {},
    onNavigateToSettings: () -> Unit,
    onSecurityLevelHelpClick: suspend () -> Unit,
    onOpenLocationSettings: () -> Unit,
    onConfigureHomeNetwork: (serverId: Int) -> Unit,
    onShowSnackbar: suspend (message: String, action: String?) -> Boolean,
) {
    if (WIPFeature.USE_FRONTEND_V2) {
        composable<FrontendRoute> {
            val viewModel: FrontendViewModel = hiltViewModel()

            FrontendNavigationHandler(
                navigationEvents = viewModel.navigationEvents,
                onNavigateToSettings = onNavigateToSettings,
                onNavigateToAssist = { serverId, pipelineId, startListening ->
                    navController.context.startActivity(
                        AssistActivity.newInstance(
                            context = navController.context,
                            serverId = serverId,
                            pipelineId = pipelineId,
                            startListening = startListening,
                        ),
                    )
                },
            )

            FrontendScreen(
                onBackClick = navController::popBackStack,
                viewModel = viewModel,
                onOpenExternalLink = onOpenExternalLink,
                onBlockInsecureHelpClick = onSecurityLevelHelpClick,
                onOpenSettings = onNavigateToSettings,
                onOpenLocationSettings = onOpenLocationSettings,
                onConfigureHomeNetwork = onConfigureHomeNetwork,
                onSecurityLevelHelpClick = onSecurityLevelHelpClick,
                onShowSnackbar = onShowSnackbar,
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
 * Handles navigation side effects for [FrontendViewState].
 */
@Composable
@VisibleForTesting
internal fun FrontendNavigationHandler(
    navigationEvents: SharedFlow<FrontendNavigationEvent>,
    onNavigateToSettings: () -> Unit,
    onNavigateToAssist: (serverId: Int, pipelineId: String?, startListening: Boolean) -> Unit,
) {
    LaunchedEffect(Unit) {
        navigationEvents.collect { event ->
            when (event) {
                is FrontendNavigationEvent.NavigateToSettings -> {
                    onNavigateToSettings()
                }

                is FrontendNavigationEvent.NavigateToAssist -> {
                    onNavigateToAssist(event.serverId, event.pipelineId, event.startListening)
                }
            }
        }
    }
}
