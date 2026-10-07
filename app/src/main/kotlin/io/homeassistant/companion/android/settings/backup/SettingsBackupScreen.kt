package io.homeassistant.companion.android.settings.backup

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.homeassistant.companion.android.common.compose.composable.HALoading
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.util.safeBottomPaddingValues
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable internal data object BackupHomeRoute

@Serializable internal data object BackupRestoreRoute

private const val BACKUP_MIME_TYPE = "application/json"
private const val BACKUP_FILE_NAME = "home-assistant-companion-settings.json"

@Composable
internal fun SettingsBackupScreen(viewModel: SettingsBackupViewModel, onManageSensors: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val resources = LocalResources.current
    val navigation = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val lifecycleOwner = LocalLifecycleOwner.current
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(BACKUP_MIME_TYPE)) {
        it?.let { uri -> viewModel.export(uri.toString()) }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        it?.let { uri -> viewModel.importBackup(uri.toString()) }
    }
    LaunchedEffect(viewModel, navigation, snackbar, lifecycleOwner, resources) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.events.collect { event ->
                when (event) {
                    BackupEvent.ShowRestore -> navigation.navigate(BackupRestoreRoute) { launchSingleTop = true }
                    BackupEvent.Restored -> navigation.popBackStack(BackupHomeRoute, inclusive = false)
                    is BackupEvent.Message -> launch { snackbar.showSnackbar(resources.getString(event.message)) }
                }
            }
        }
    }
    Scaffold(
        containerColor = LocalHAColorScheme.current.colorSurfaceDefault,
        contentWindowInsets = WindowInsets(),
        snackbarHost = { SnackbarHost(snackbar, Modifier.padding(safeBottomPaddingValues())) },
    ) { padding ->
        BackupNavigation(
            state = state,
            navigation = navigation,
            onSelect = viewModel::select,
            onExport = { exporter.launch(BACKUP_FILE_NAME) },
            onImport = { importer.launch(arrayOf(BACKUP_MIME_TYPE, "text/plain")) },
            onMapServer = viewModel::mapServer,
            onReview = viewModel::review,
            onRestore = viewModel::restore,
            onCancel = viewModel::dismissRestore,
            onRetry = viewModel::load,
            onManageSensors = onManageSensors,
            modifier = Modifier.padding(padding),
        )
    }
}

@Composable
internal fun BackupNavigation(
    state: SettingsBackupUiState,
    navigation: androidx.navigation.NavHostController,
    onSelect: (BackupSection, Boolean) -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onMapServer: (String, BackupServerTarget) -> Unit,
    onReview: () -> Unit,
    onRestore: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onManageSensors: () -> Unit,
    modifier: Modifier = Modifier,
) {
    NavHost(navigation, startDestination = BackupHomeRoute, modifier = modifier) {
        composable<BackupHomeRoute> {
            when (state) {
                SettingsBackupUiState.Loading -> HALoading()
                SettingsBackupUiState.Error -> BackupErrorContent(onRetry)
                is SettingsBackupUiState.Content -> BackupHomeContent(
                    state,
                    onSelect,
                    onExport,
                    onImport,
                    onManageSensors,
                )
            }
            BackHandler(enabled = (state as? SettingsBackupUiState.Content)?.busy == true) { /* Wait for the write. */ }
        }
        composable<BackupRestoreRoute> {
            val cancel = {
                onCancel()
                navigation.popBackStack(BackupHomeRoute, inclusive = false)
                Unit
            }
            BackupRestoreContent(
                state = state as? SettingsBackupUiState.Content,
                onSelect = onSelect,
                onMapServer = onMapServer,
                onReview = onReview,
                onRestore = onRestore,
                onCancel = cancel,
            )
            BackHandler {
                if ((state as? SettingsBackupUiState.Content)?.busy != true) cancel()
            }
        }
    }
}
