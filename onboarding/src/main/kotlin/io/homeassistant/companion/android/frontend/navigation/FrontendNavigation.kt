package io.homeassistant.companion.android.frontend.navigation

import android.content.ComponentName
import androidx.navigation.ActivityNavigator
import androidx.navigation.ActivityNavigatorDestinationBuilder
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavOptions
import androidx.navigation.get
import io.homeassistant.companion.android.HAStartDestinationRoute
import io.homeassistant.companion.android.common.data.servers.ServerManager.Companion.SERVER_ID_ACTIVE
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class FrontendRoute(
    val path: String? = null,
    // Override the serial name to match the name in WebViewActivity
    @SerialName("server") val serverId: Int = SERVER_ID_ACTIVE,
) : HAStartDestinationRoute

internal fun NavController.navigateToFrontend(
    path: String? = null,
    serverId: Int = SERVER_ID_ACTIVE,
    navOptions: NavOptions? = null,
) {
    navigate(FrontendRoute(path, serverId), navOptions)
}

/**
 * Destination to the WebviewActivity with the [FrontendRoute.path] and [FrontendRoute.serverId] parameters.
 */
internal fun NavGraphBuilder.frontendScreen(navController: NavHostController) {
    // TODO replace with strong types when WebViewActivity is available to onboarding module
    // Inspired from activity<T> { } to be able to give a ComponentName instead of a class since :onboarding doesn't know :app
    val destination = ActivityNavigatorDestinationBuilder(
        provider[ActivityNavigator::class],
        FrontendRoute::class,
        emptyMap(),
    ).build().setComponentName(
        ComponentName(
            navController.context,
            "io.homeassistant.companion.android.webview.WebViewActivity",
        ),
    )

    addDestination(destination)
}
