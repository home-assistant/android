package io.homeassistant.companion.android.onboarding.wearmtls.navigation

import android.net.Uri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import io.homeassistant.companion.android.onboarding.wearmtls.WearMTLSScreen
import kotlinx.serialization.Serializable

@Serializable
internal class WearMTLSRoute(val deviceName: String, val serverUrl: String, val authCode: String)

internal fun NavController.navigateToWearMTLS(
    deviceName: String,
    serverUrl: String,
    authCode: String,
    navOptions: NavOptions? = null,
) {
    navigate(
        WearMTLSRoute(deviceName = deviceName, serverUrl = serverUrl, authCode = authCode),
        navOptions = navOptions,
    )
}

internal fun NavGraphBuilder.wearMTLSScreen(
    onHelpClick: () -> Unit,
    onBackClick: () -> Unit,
    onNext: (deviceName: String, serverUrl: String, authCode: String, certUri: Uri, certPassword: String) -> Unit,
) {
    composable<WearMTLSRoute> {
        val route = it.toRoute<WearMTLSRoute>()

        WearMTLSScreen(
            onHelpClick = onHelpClick,
            onBackClick = onBackClick,
            onNext = { certUri, certPassword ->
                onNext(route.deviceName, route.serverUrl, route.authCode, certUri, certPassword)
            },
            viewModel = hiltViewModel(),
        )
    }
}
