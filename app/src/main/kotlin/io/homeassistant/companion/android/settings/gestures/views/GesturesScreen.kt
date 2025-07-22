package io.homeassistant.companion.android.settings.gestures.views

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import io.homeassistant.companion.android.common.R
import io.homeassistant.companion.android.common.util.GestureAction
import io.homeassistant.companion.android.common.util.HAGesture
import kotlinx.serialization.Serializable

@Serializable private data object GesturesRoute

@Serializable private data class ActionsRoute(val gesture: HAGesture)

/**
 * Main Composable for gesture settings, allowing navigation to:
 *  - Gestures list (a list of all gestures with their action)
 *  - Gesture actions (when a gesture is selected, to change the action for it)
 *
 *  @param gestureActions User settings (gestures and the current action)
 *  @param onSetAction Called when the action for a gesture should be changed
 *  @param onToolbarTitleChanged Called when the screen changes, to update the
 *         Activity's Toolbar title
 */
@Composable
fun GesturesScreen(
    gestureActions: Map<HAGesture, GestureAction>,
    onSetAction: (HAGesture, GestureAction) -> Unit,
    onToolbarTitleChanged: (String) -> Unit,
) {
    val context = LocalContext.current
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = GesturesRoute,
        // The apps' settings fragments do not have a transition. Compose Navigation forces a transition when
        // using predictive back linked to swipe progress, so we cannot set this to no transition as the UI
        // will look broken otherwise. To make the difference between fragments and Compose less jarring,
        // use a very short crossfade.
        enterTransition = { fadeIn(tween(200)) },
        exitTransition = { fadeOut(tween(200)) },
    ) {
        composable<GesturesRoute> {
            GesturesListView(
                gestureActions = gestureActions,
                onGestureClicked = { gesture ->
                    navController.navigate(ActionsRoute(gesture))
                },
            )
        }
        composable<ActionsRoute> { backStackEntry ->
            val action: ActionsRoute = backStackEntry.toRoute()
            GestureActionsView(
                selectedAction = gestureActions.getOrDefault(action.gesture, GestureAction.NONE),
                onActionClicked = { newAction ->
                    onSetAction(action.gesture, newAction)
                    navController.popBackStack(route = GesturesRoute, inclusive = false)
                },
            )
        }
    }
    LaunchedEffect(Unit) {
        navController.currentBackStackEntryFlow.collect { backStackEntry ->
            if (backStackEntry.destination.hasRoute(route = GesturesRoute::class)) {
                onToolbarTitleChanged(context.getString(R.string.gestures))
            } else if (backStackEntry.destination.hasRoute(ActionsRoute::class)) {
                val action: ActionsRoute = backStackEntry.toRoute()
                onToolbarTitleChanged(context.getString(action.gesture.fullDescription))
            }
        }
    }
}
