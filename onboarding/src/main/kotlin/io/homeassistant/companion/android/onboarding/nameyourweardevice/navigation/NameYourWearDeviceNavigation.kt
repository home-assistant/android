package io.homeassistant.companion.android.onboarding.nameyourweardevice.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import io.homeassistant.companion.android.onboarding.nameyourdevice.NameYourDeviceScreen
import kotlinx.serialization.Serializable

@Serializable
internal data class NameYourWearDeviceRoute(
    val defaultDeviceName: String,
    val url: String,
    val authCode: String,
    val requiredMTLS: Boolean,
)

internal fun NavController.navigateToNameYourWearDevice(
    defaultDeviceName: String,
    url: String,
    authCode: String,
    requiredMTLS: Boolean,
    navOptions: NavOptions? = null,
) {
    navigate(
        route = NameYourWearDeviceRoute(defaultDeviceName, url, authCode, requiredMTLS),
        navOptions,
    )
}

internal fun NavGraphBuilder.nameYourWearDeviceScreen(
    onBackClick: () -> Unit,
    onHelpClick: () -> Unit,
    onDeviceNamed: (deviceName: String, serverUrl: String, authCode: String, neededMTLS: Boolean) -> Unit,
) {
    composable<NameYourWearDeviceRoute> {
        val route = it.toRoute<NameYourWearDeviceRoute>()
        // Use rememberSaveable to retain the device name across configuration changes or when navigating
        // away and returning to this screen. This prevents the loss of user input and avoids reverting
        // to the default device name.
        // If the logic of the screen evolve we should consider introducing a viewModel instead.
        var deviceName by rememberSaveable { mutableStateOf(route.defaultDeviceName) }

        NameYourDeviceScreen(
            onBackClick = onBackClick,
            onHelpClick = onHelpClick,
            deviceName = deviceName,
            saveClickable = true,
            deviceNameEditable = true,
            onDeviceNameChange = { name -> deviceName = name },
            onSaveClick = {
                onDeviceNamed(deviceName, route.url, route.authCode, route.requiredMTLS)
            },
        )
    }
}
