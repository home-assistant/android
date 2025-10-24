package io.homeassistant.companion.android.frontend.navigation

import android.content.ComponentName
import androidx.activity.compose.LocalActivity
import androidx.navigation.ActivityNavigator
import androidx.navigation.ActivityNavigatorDestinationBuilder
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import androidx.navigation.get
import androidx.navigation.toRoute
import io.homeassistant.companion.android.HAStartDestinationRoute
import io.homeassistant.companion.android.common.data.servers.ServerManager.Companion.SERVER_ID_ACTIVE
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class FrontendActivityRoute(
    val path: String? = null,
    // Override the serial name to match the name in WebViewActivity
    @SerialName("server") val serverId: Int = SERVER_ID_ACTIVE,
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
 */
internal fun NavGraphBuilder.frontendScreen(navController: NavController) {
    composable<FrontendRoute> {
        val dummy = it.toRoute<FrontendRoute>()
        navController.navigate(FrontendActivityRoute(dummy.path, dummy.serverId))
        val activity = LocalActivity.current
        activity?.finish()
    }

    // TODO replace with strong types when WebViewActivity is available to onboarding module
    // Inspired from activity<T> { } to be able to give a ComponentName instead of a class since :onboarding doesn't know :app
    val destination = ActivityNavigatorDestinationBuilder(
        provider[ActivityNavigator::class],
        FrontendActivityRoute::class,
        emptyMap(),
    ).build().setComponentName(
        ComponentName(
            navController.context,
            "io.homeassistant.companion.android.webview.WebViewActivity",
        ),
    )

    addDestination(destination)
}
