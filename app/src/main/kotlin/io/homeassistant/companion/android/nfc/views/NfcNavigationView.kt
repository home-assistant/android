package io.homeassistant.companion.android.nfc.views

import android.content.Intent
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Scaffold
import androidx.compose.material.SnackbarHost
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.rememberScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.nfc.NfcSetupActivity
import io.homeassistant.companion.android.nfc.NfcViewModel
import io.homeassistant.companion.android.util.safeBottomWindowInsets
import io.homeassistant.companion.android.util.safeTopWindowInsets
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@Composable
fun LoadNfcView(viewModel: NfcViewModel, startDestination: String, pressedUpAtRoot: () -> Unit) {
    val context = LocalContext.current

    val navController = rememberNavController()
    val canNavigateUp = remember { mutableStateOf(false) }
    navController.addOnDestinationChangedListener { controller, destination, _ ->
        canNavigateUp.value = controller.previousBackStackEntry != null
        viewModel.setDestination(destination.route)
    }
    LaunchedEffect("navigation") {
        viewModel.navigator.flow.onEach {
            navController.navigate(it.id) {
                if (it.popBackstackTo != null) {
                    popUpTo(it.popBackstackTo) { inclusive = it.popBackstackInclusive }
                }
            }
        }.launchIn(this)
    }

    val scaffoldState = rememberScaffoldState()
    LaunchedEffect("snackbar") {
        viewModel.nfcResultSnackbar.onEach {
            if (it != 0) {
                scaffoldState.snackbarHostState.showSnackbar(context.getString(it))
            }
        }.launchIn(this)
    }

    Scaffold(
        scaffoldState = scaffoldState,
        snackbarHost = {
            SnackbarHost(
                hostState = scaffoldState.snackbarHostState,
                modifier = Modifier.windowInsetsPadding(safeBottomWindowInsets()),
            )
        },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(commonR.string.nfc_title_settings)) },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (canNavigateUp.value) {
                                navController.navigateUp()
                            } else {
                                pressedUpAtRoot()
                            }
                        },
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(commonR.string.navigate_up),
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            val intent =
                                Intent(
                                    Intent.ACTION_VIEW,
                                    "https://companion.home-assistant.io/docs/integrations/universal-links".toUri(),
                                )
                            context.startActivity(intent)
                        },
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.HelpOutline,
                            contentDescription = stringResource(commonR.string.get_help),
                            tint = colorResource(commonR.color.colorOnBackground),
                        )
                    }
                },
                backgroundColor = colorResource(commonR.color.colorBackground),
                contentColor = colorResource(commonR.color.colorOnBackground),
                windowInsets = safeTopWindowInsets(),
            )
        },
    ) { contentPadding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(contentPadding),
        ) {
            composable(NfcSetupActivity.NAV_WELCOME) {
                NfcWelcomeView(
                    isNfcEnabled = viewModel.isNfcEnabled,
                    onReadClicked = { viewModel.navigator.navigateTo(NfcSetupActivity.NAV_READ) },
                    onWriteClicked = { viewModel.writeNewTag() },
                )
            }
            composable(NfcSetupActivity.NAV_READ) {
                NfcReadView()
            }
            composable(NfcSetupActivity.NAV_WRITE) {
                NfcWriteView(
                    isNfcEnabled = viewModel.isNfcEnabled,
                    identifier = viewModel.nfcTagIdentifier,
                    onSetIdentifier = if (viewModel.nfcIdentifierIsEditable) {
                        { viewModel.setTagIdentifier(it) }
                    } else {
                        null
                    },
                )
            }
            composable(NfcSetupActivity.NAV_EDIT) {
                NfcEditView(
                    identifier = viewModel.nfcTagIdentifier,
                    showDeviceSample = viewModel.usesAndroidDeviceId,
                    onDuplicateClicked = { viewModel.duplicateNfcTag() },
                    onFireEventClicked = { viewModel.fireNfcTagEvent() },
                )
            }
        }
    }
}
