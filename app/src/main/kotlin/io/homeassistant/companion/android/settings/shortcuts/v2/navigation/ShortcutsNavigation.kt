package io.homeassistant.companion.android.settings.shortcuts.v2.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import io.homeassistant.companion.android.common.R
import io.homeassistant.companion.android.settings.shortcuts.v2.EditShortcutViewModel
import io.homeassistant.companion.android.settings.shortcuts.v2.ManageShortcutsViewModel
import io.homeassistant.companion.android.settings.shortcuts.v2.ShortcutsListAction
import io.homeassistant.companion.android.settings.shortcuts.v2.views.screens.ShortcutEditorScreen
import io.homeassistant.companion.android.settings.shortcuts.v2.views.screens.ShortcutsListScreen
import kotlinx.serialization.Serializable

@Serializable
private data object ShortcutsListRoute

@Serializable
private data object CreateAppShortcutRoute

@Serializable
private data object CreateHomeShortcutRoute

@Serializable
private data class EditAppShortcutRoute(val index: Int)

@Serializable
private data class EditHomeShortcutRoute(val id: String)

@Composable
fun ShortcutsNavHost(onToolbarTitleChanged: (String) -> Unit) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = ShortcutsListRoute,
    ) {
        composable<ShortcutsListRoute> {
            ShortcutsListRouteScreen(
                onNavigate = { action ->
                    when (action) {
                        is ShortcutsListAction.EditAppShortcut -> navController.navigate(
                            EditAppShortcutRoute(action.index),
                        )

                        is ShortcutsListAction.EditHomeShortcut -> navController.navigate(
                            EditHomeShortcutRoute(action.id),
                        )

                        is ShortcutsListAction.CreateAppShortcut -> navController.navigate(CreateAppShortcutRoute)

                        is ShortcutsListAction.CreateHomeShortcut -> navController.navigate(CreateHomeShortcutRoute)
                    }
                },
            )
        }

        composable<CreateAppShortcutRoute> {
            CreateAppShortcutRouteScreen(
                onNavigateBack = { navController.popBackStack() },
            )
        }

        composable<CreateHomeShortcutRoute> {
            CreateHomeShortcutRouteScreen(
                onNavigateBack = { navController.popBackStack() },
            )
        }

        composable<EditAppShortcutRoute> { backStackEntry ->
            val route: EditAppShortcutRoute = backStackEntry.toRoute()
            EditAppShortcutRouteScreen(
                route = route,
                onNavigateBack = { navController.popBackStack() },
            )
        }

        composable<EditHomeShortcutRoute> { backStackEntry ->
            val route: EditHomeShortcutRoute = backStackEntry.toRoute()
            EditHomeShortcutRouteScreen(
                route = route,
                onNavigateBack = { navController.popBackStack() },
            )
        }
    }
    val shortcutsTitle = stringResource(R.string.shortcuts)
    val addAppShortcutTitle = stringResource(R.string.shortcut_v2_add_app_shortcut_title)
    val addHomeShortcutTitle = stringResource(R.string.shortcut_v2_add_home_shortcut_title)
    val editAppShortcutTitle = stringResource(R.string.shortcut_v2_edit_app_shortcut_title)
    val editHomeShortcutTitle = stringResource(R.string.shortcut_v2_edit_home_shortcut_title)
    LaunchedEffect(navController) {
        navController.currentBackStackEntryFlow.collect { backStackEntry ->
            when {
                backStackEntry.destination.hasRoute(route = ShortcutsListRoute::class) -> {
                    onToolbarTitleChanged(shortcutsTitle)
                }

                backStackEntry.destination.hasRoute(route = CreateAppShortcutRoute::class) -> {
                    onToolbarTitleChanged(addAppShortcutTitle)
                }

                backStackEntry.destination.hasRoute(route = CreateHomeShortcutRoute::class) -> {
                    onToolbarTitleChanged(addHomeShortcutTitle)
                }

                backStackEntry.destination.hasRoute(route = EditAppShortcutRoute::class) -> {
                    onToolbarTitleChanged(editAppShortcutTitle)
                }

                backStackEntry.destination.hasRoute(route = EditHomeShortcutRoute::class) -> {
                    onToolbarTitleChanged(editHomeShortcutTitle)
                }
            }
        }
    }
}

@Composable
private fun ShortcutsListRouteScreen(
    onNavigate: (ShortcutsListAction) -> Unit,
    viewModel: ManageShortcutsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshSilently()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    ShortcutsListScreen(
        state = uiState,
        dispatch = onNavigate,
        onRetry = viewModel::refresh,
    )
}

@Composable
private fun CreateAppShortcutRouteScreen(
    viewModel: EditShortcutViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.createAppShortcutFirstAvailable()
    }

    LaunchedEffect(viewModel) {
        viewModel.closeEvents.collect {
            onNavigateBack()
        }
    }

    ShortcutEditorScreen(
        state = uiState,
        dispatch = viewModel::dispatch,
        onRetry = viewModel::createAppShortcutFirstAvailable,
    )
}

@Composable
private fun CreateHomeShortcutRouteScreen(
    viewModel: EditShortcutViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.openCreateHomeShortcut()
    }

    LaunchedEffect(viewModel) {
        viewModel.closeEvents.collect {
            onNavigateBack()
        }
    }

    ShortcutEditorScreen(
        state = uiState,
        dispatch = viewModel::dispatch,
        onRetry = viewModel::openCreateHomeShortcut,
    )
}

@Composable
private fun EditAppShortcutRouteScreen(
    route: EditAppShortcutRoute,
    viewModel: EditShortcutViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(route.index) {
        viewModel.openEditAppShortcut(route.index)
    }

    LaunchedEffect(viewModel) {
        viewModel.closeEvents.collect {
            onNavigateBack()
        }
    }

    ShortcutEditorScreen(
        state = uiState,
        dispatch = viewModel::dispatch,
        onRetry = { viewModel.openEditAppShortcut(route.index) },
    )
}

@Composable
private fun EditHomeShortcutRouteScreen(
    route: EditHomeShortcutRoute,
    onNavigateBack: () -> Unit,
    viewModel: EditShortcutViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(route.id) {
        viewModel.openEditHomeShortcut(route.id)
    }

    LaunchedEffect(viewModel) {
        viewModel.closeEvents.collect {
            onNavigateBack()
        }
    }

    ShortcutEditorScreen(
        state = uiState,
        dispatch = viewModel::dispatch,
        onRetry = { viewModel.openEditHomeShortcut(route.id) },
    )
}
