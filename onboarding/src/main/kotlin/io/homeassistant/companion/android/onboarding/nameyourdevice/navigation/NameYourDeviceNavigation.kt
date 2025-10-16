package io.homeassistant.companion.android.onboarding.nameyourdevice.navigation

import androidx.annotation.VisibleForTesting
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import io.homeassistant.companion.android.onboarding.nameyourdevice.NameYourDeviceNavigationEvent
import io.homeassistant.companion.android.onboarding.nameyourdevice.NameYourDeviceScreen
import io.homeassistant.companion.android.onboarding.nameyourdevice.NameYourDeviceViewModel
import kotlinx.serialization.Serializable

@Serializable
internal data class NameYourDeviceRoute(val url: String, val authCode: String)

internal fun NavController.navigateToNameYourDevice(url: String, authCode: String, navOptions: NavOptions? = null) {
    navigate(route = NameYourDeviceRoute(url, authCode), navOptions)
}

internal fun NavGraphBuilder.nameYourDeviceScreen(
    onBackClick: () -> Unit,
    onDeviceNamed: (serverId: Int, hasPlainTextAccess: Boolean, isPubliclyAccessible: Boolean) -> Unit,
    onShowSnackbar: suspend (message: String, action: String?) -> Boolean,
    onHelpClick: () -> Unit,
) {
    composable<NameYourDeviceRoute> {
        val viewModel: NameYourDeviceViewModel = hiltViewModel()

        HandleNameYourDeviceNavigationEvents(
            viewModel = viewModel,
            onDeviceNamed = onDeviceNamed,
            onShowSnackbar = onShowSnackbar,
            onBackClick = onBackClick,
        )

        NameYourDeviceScreen(
            onBackClick = onBackClick,
            onHelpClick = onHelpClick,
            viewModel = viewModel,
        )
    }
}

@Composable
@VisibleForTesting
internal fun HandleNameYourDeviceNavigationEvents(
    viewModel: NameYourDeviceViewModel,
    onDeviceNamed: (serverId: Int, hasPlainTextAccess: Boolean, isPubliclyAccessible: Boolean) -> Unit,
    onShowSnackbar: suspend (message: String, action: String?) -> Boolean,
    onBackClick: () -> Unit,
) {
    val context = LocalContext.current

    LaunchedEffect(viewModel) {
        viewModel.navigationEventsFlow.collect {
            when (it) {
                is NameYourDeviceNavigationEvent.DeviceNameSaved -> onDeviceNamed(
                    it.serverId,
                    it.hasPlainTextAccess,
                    it.isPubliclyAccessible,
                )

                is NameYourDeviceNavigationEvent.Error -> {
                    onShowSnackbar(context.getString(it.messageRes), null)
                    onBackClick()
                }
            }
        }
    }
}
