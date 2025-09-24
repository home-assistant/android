package io.homeassistant.companion.android.automotive.navigation

import android.content.ComponentName
import android.content.Intent
import androidx.navigation.ActivityNavigator
import androidx.navigation.ActivityNavigatorDestinationBuilder
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.get
import io.homeassistant.companion.android.HAStartDestinationRoute
import kotlinx.serialization.Serializable

@Serializable
internal data object AutomotiveRoute : HAStartDestinationRoute

internal fun NavController.navigateToCarAppActivity(navOptions: NavOptions? = null) {
    navigate(route = AutomotiveRoute, navOptions)
}

internal fun NavGraphBuilder.carAppActivity(navController: NavController) {
    // Inspired from activity<T> { } to be able to give a ComponentName instead of a class since :onboarding doesn't know :automotive
    val destination = ActivityNavigatorDestinationBuilder(
        provider[ActivityNavigator::class],
        AutomotiveRoute::class,
        emptyMap(),
    ).build()
        .setIntent(Intent().addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        .setComponentName(
            ComponentName(
                navController.context,
                "androidx.car.app.activity.CarAppActivity",
            ),
        )

    addDestination(destination)
}
