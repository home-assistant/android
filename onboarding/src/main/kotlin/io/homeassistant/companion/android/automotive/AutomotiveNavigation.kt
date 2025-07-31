package io.homeassistant.companion.android.automotive

import android.content.Intent
import androidx.navigation.ActivityNavigator
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.activity
import kotlinx.serialization.Serializable

@Serializable
data object AutomotiveRoute

fun NavController.navigateToCarAppActivity(
    navOptions: NavOptions? = null,
    navExtras: ActivityNavigator.Extras = ActivityNavigator.Extras.Builder().addFlags(
        Intent.FLAG_ACTIVITY_NEW_TASK,
    ).build(),
) {
    navigate(AutomotiveRoute, navOptions = navOptions, navigatorExtras = navExtras)
}

fun NavGraphBuilder.carAppActivity() {
    activity<AutomotiveRoute> {
        // activityClass = CarAppActivity::class
    }
}
