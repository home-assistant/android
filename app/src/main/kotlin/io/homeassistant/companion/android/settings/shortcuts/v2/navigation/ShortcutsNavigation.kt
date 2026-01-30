package io.homeassistant.companion.android.settings.shortcuts.v2.navigation

import android.os.Build
import androidx.annotation.RequiresApi
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
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.PinResult
import io.homeassistant.companion.android.settings.shortcuts.v2.ShortcutEditViewModel
import io.homeassistant.companion.android.settings.shortcuts.v2.ShortcutsListAction
import io.homeassistant.companion.android.settings.shortcuts.v2.ShortcutsViewModel
import io.homeassistant.companion.android.settings.shortcuts.v2.ui.screens.ShortcutEditorScreen
import io.homeassistant.companion.android.settings.shortcuts.v2.ui.screens.ShortcutsScreen
import kotlinx.serialization.Serializable

@Serializable
private data object ShortcutsListRoute

@Serializable
private data object CreateDynamicRoute

@Serializable
private data object CreatePinnedRoute

@Serializable
private data class EditDynamicRoute(val index: Int)

@Serializable
private data class EditPinnedRoute(val id: String)

@RequiresApi(Build.VERSION_CODES.N_MR1)
@Composable
fun ShortcutsNavHost(onToolbarTitleChanged: (String) -> Unit, onShowSnackbar: suspend (message: String) -> Unit) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = ShortcutsListRoute,
    ) {
        composable<ShortcutsListRoute> {
            ShortcutsListRouteScreen(
                onNavigate = { action ->
                    when (action) {
                        is ShortcutsListAction.EditDynamic -> navController.navigate(EditDynamicRoute(action.index))
                        is ShortcutsListAction.EditPinned -> navController.navigate(EditPinnedRoute(action.id))
                        ShortcutsListAction.CreateDynamic -> navController.navigate(CreateDynamicRoute)
                        ShortcutsListAction.CreatePinned -> navController.navigate(CreatePinnedRoute)
                    }
                },
            )
        }

        composable<CreateDynamicRoute> {
            CreateDynamicRouteScreen()
        }

        composable<CreatePinnedRoute> {
            CreatePinnedRouteScreen(
                onShowSnackbar = onShowSnackbar,
                onNavigateBack = { navController.popBackStack() },
            )
        }

        composable<EditDynamicRoute> { backStackEntry ->
            val route: EditDynamicRoute = backStackEntry.toRoute()
            EditDynamicRouteScreen(
                route = route,
                onNavigateBack = { navController.popBackStack() },
            )
        }

        composable<EditPinnedRoute> { backStackEntry ->
            val route: EditPinnedRoute = backStackEntry.toRoute()
            EditPinnedRouteScreen(
                route = route,
                onNavigateBack = { navController.popBackStack() },
                onShowSnackbar = onShowSnackbar,
            )
        }
    }
    val shortcutsTitle = stringResource(R.string.shortcuts)
    val addShortcutsTitle = stringResource(R.string.add_shortcut)
    val updateShortcutsTitle = stringResource(R.string.update_shortcut)
    LaunchedEffect(navController) {
        navController.currentBackStackEntryFlow.collect { backStackEntry ->
            when {
                backStackEntry.destination.hasRoute(route = ShortcutsListRoute::class) -> {
                    onToolbarTitleChanged(shortcutsTitle)
                }

                backStackEntry.destination.hasRoute(route = CreateDynamicRoute::class) ||
                    backStackEntry.destination.hasRoute(route = CreatePinnedRoute::class) -> {
                    onToolbarTitleChanged(addShortcutsTitle)
                }

                backStackEntry.destination.hasRoute(route = EditDynamicRoute::class) ||
                    backStackEntry.destination.hasRoute(route = EditPinnedRoute::class) -> {
                    onToolbarTitleChanged(updateShortcutsTitle)
                }
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.N_MR1)
@Composable
private fun ShortcutsListRouteScreen(
    onNavigate: (ShortcutsListAction) -> Unit,
    viewModel: ShortcutsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refresh(showLoading = false)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    ShortcutsScreen(
        state = uiState,
        dispatch = onNavigate,
    )
}

@RequiresApi(Build.VERSION_CODES.N_MR1)
@Composable
private fun CreateDynamicRouteScreen(viewModel: ShortcutEditViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.createDynamicFirstAvailable()
    }

    ShortcutEditorScreen(
        state = uiState,
        dispatch = viewModel::dispatch,
    )
}

@RequiresApi(Build.VERSION_CODES.N_MR1)
@Composable
private fun CreatePinnedRouteScreen(
    viewModel: ShortcutEditViewModel = hiltViewModel(),
    onShowSnackbar: suspend (message: String) -> Unit,
    onNavigateBack: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val shortcutPinRequestedMessage = stringResource(R.string.shortcut_pin_requested)
    val shortcutUpdatedMessage = stringResource(R.string.shortcut_updated)
    val shortcutPinNotSupportedMessage = stringResource(R.string.shortcut_pin_not_supported)

    LaunchedEffect(Unit) {
        viewModel.openCreatePinned()
    }

    LaunchedEffect(viewModel) {
        viewModel.pinResultEvents.collect { result ->
            val message = when (result) {
                PinResult.Requested -> shortcutPinRequestedMessage
                PinResult.Updated -> shortcutUpdatedMessage
                PinResult.NotSupported -> shortcutPinNotSupportedMessage
            }
            onShowSnackbar(message)
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.closeEvents.collect {
            onNavigateBack()
        }
    }

    ShortcutEditorScreen(
        state = uiState,
        dispatch = viewModel::dispatch,
    )
}

@RequiresApi(Build.VERSION_CODES.N_MR1)
@Composable
private fun EditDynamicRouteScreen(
    route: EditDynamicRoute,
    viewModel: ShortcutEditViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(route.index) {
        viewModel.openDynamic(route.index)
    }

    LaunchedEffect(viewModel) {
        viewModel.closeEvents.collect {
            onNavigateBack()
        }
    }

    ShortcutEditorScreen(
        state = uiState,
        dispatch = viewModel::dispatch,
    )
}

@RequiresApi(Build.VERSION_CODES.N_MR1)
@Composable
private fun EditPinnedRouteScreen(
    route: EditPinnedRoute,
    onNavigateBack: () -> Unit,
    viewModel: ShortcutEditViewModel = hiltViewModel(),
    onShowSnackbar: suspend (message: String) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val shortcutUpdatedMessage = stringResource(R.string.shortcut_updated)
    val shortcutPinRequestedMessage = stringResource(R.string.shortcut_pin_requested)
    val shortcutPinNotSupportedMessage = stringResource(R.string.shortcut_pin_not_supported)

    LaunchedEffect(route.id) {
        viewModel.editPinned(route.id)
    }

    LaunchedEffect(viewModel) {
        viewModel.pinResultEvents.collect { result ->
            val message = when (result) {
                PinResult.Requested -> shortcutPinRequestedMessage
                PinResult.Updated -> shortcutUpdatedMessage
                PinResult.NotSupported -> shortcutPinNotSupportedMessage
            }
            onShowSnackbar(message)
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.closeEvents.collect {
            onNavigateBack()
        }
    }

    ShortcutEditorScreen(
        state = uiState,
        dispatch = viewModel::dispatch,
    )
}
