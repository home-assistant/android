package io.homeassistant.companion.android.frontend.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.activity
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import io.homeassistant.companion.android.common.data.servers.ServerManager.Companion.SERVER_ID_ACTIVE
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
 * The actual navigation to the frontend is done by navigating to [FrontendActivityRoute] with the
 * `path` and `serverId` parameters. This will launch the `WebViewActivity` (which is in `:app`).
 *
 * To ensure that the activity that starts the `WebViewActivity` is finished, users should navigate
 * to [FrontendRoute]. This route will then navigate to [FrontendActivityRoute] and finish the
 * current activity. This behavior is necessary until `WebViewActivity` is replaced with a
 * composable NavGraph entry, allowing for more direct navigation.
 *
 * Note: Security level verification is handled by [WebViewActivity] before loading any URL.
 * If the security level is not set, [WebViewActivity] will show the
 * [io.homeassistant.companion.android.settings.ConnectionSecurityLevelFragment].
 */
internal fun NavGraphBuilder.frontendScreen(navController: NavController) {
    composable<FrontendRoute> {
        val route = it.toRoute<FrontendRoute>()
        navController.navigate(FrontendActivityRoute(route.serverId, route.path))
        navController.context.getActivity()?.finish()
    }

    activity<FrontendActivityRoute> {
        activityClass = WebViewActivity::class
    }
}
