package io.homeassistant.companion.android.frontend

import android.annotation.SuppressLint
import android.webkit.WebChromeClient
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import com.wifi.improv.DeviceState
import com.wifi.improv.ErrorState
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.frontend.improv.ImprovUIState
import io.homeassistant.companion.android.frontend.js.FrontendJsBridge
import io.homeassistant.companion.android.frontend.permissions.PermissionRequest
import io.homeassistant.companion.android.util.compose.HAPreviews

/**
 * Screenshot coverage for the improv flow as composed inside [FrontendScreenContent]: both the
 * onboarding bottom sheet (driven by [ImprovUIState] variants) and the permission rationale
 * prompt (driven by [PermissionRequest.Improv] with `showRationale = true`).
 */
@SuppressLint("InlinedApi")
class FrontendScreenImprovScreenshotTest {

    @PreviewTest
    @HAPreviews
    @Composable
    fun `FrontendScreen Improv SearchingDevice`() {
        HAThemeForPreview {
            FrontendImprovContent(
                improvUiState = ImprovUIState.SearchingDevice(deviceName = "Smart Plug"),
            )
        }
    }

    @PreviewTest
    @HAPreviews
    @Composable
    fun `FrontendScreen Improv ConfiguringDevice`() {
        HAThemeForPreview {
            FrontendImprovContent(
                improvUiState = ImprovUIState.ConfiguringDevice(
                    deviceName = "Smart Plug",
                    deviceAddress = "A1:B2:C3:D4:E5:F6",
                    activeSsid = "Home Wi-Fi",
                ),
            )
        }
    }

    @PreviewTest
    @HAPreviews
    @Composable
    fun `FrontendScreen Improv Provisioning connecting`() {
        HAThemeForPreview {
            FrontendImprovContent(
                improvUiState = ImprovUIState.Provisioning(
                    deviceName = "Smart Plug",
                    deviceAddress = "A1:B2:C3:D4:E5:F6",
                    state = null,
                ),
            )
        }
    }

    @PreviewTest
    @HAPreviews
    @Composable
    fun `FrontendScreen Improv Provisioning authorization required`() {
        HAThemeForPreview {
            FrontendImprovContent(
                improvUiState = ImprovUIState.Provisioning(
                    deviceName = "Smart Plug",
                    deviceAddress = "A1:B2:C3:D4:E5:F6",
                    state = DeviceState.AUTHORIZATION_REQUIRED,
                ),
            )
        }
    }

    @PreviewTest
    @HAPreviews
    @Composable
    fun `FrontendScreen Improv Provisioning sending wifi`() {
        HAThemeForPreview {
            FrontendImprovContent(
                improvUiState = ImprovUIState.Provisioning(
                    deviceName = "Smart Plug",
                    deviceAddress = "A1:B2:C3:D4:E5:F6",
                    state = DeviceState.PROVISIONING,
                ),
            )
        }
    }

    @PreviewTest
    @HAPreviews
    @Composable
    fun `FrontendScreen Improv Errored unable to connect`() {
        HAThemeForPreview {
            FrontendImprovContent(
                improvUiState = ImprovUIState.Errored(
                    deviceName = "Smart Plug",
                    deviceAddress = "A1:B2:C3:D4:E5:F6",
                    error = ErrorState.UNABLE_TO_CONNECT,
                ),
            )
        }
    }

    @PreviewTest
    @HAPreviews
    @Composable
    fun `FrontendScreen Improv Errored not authorized`() {
        HAThemeForPreview {
            FrontendImprovContent(
                improvUiState = ImprovUIState.Errored(
                    deviceName = "Smart Plug",
                    deviceAddress = "A1:B2:C3:D4:E5:F6",
                    error = ErrorState.NOT_AUTHORIZED,
                ),
            )
        }
    }

    @PreviewTest
    @HAPreviews
    @Composable
    fun `FrontendScreen Improv Provisioned`() {
        HAThemeForPreview {
            FrontendImprovContent(
                improvUiState = ImprovUIState.Provisioned(domain = "esphome"),
            )
        }
    }

    @PreviewTest
    @HAPreviews
    @Composable
    fun `FrontendScreen Improv permission rationale Bluetooth and Location`() {
        HAThemeForPreview {
            FrontendImprovContent(
                pendingPermissionRequest = PermissionRequest.Improv(
                    permissions = listOf(
                        android.Manifest.permission.BLUETOOTH_SCAN,
                        android.Manifest.permission.ACCESS_FINE_LOCATION,
                    ),
                    showRationale = true,
                    onResult = {},
                    onDismiss = {},
                ),
            )
        }
    }

    @PreviewTest
    @HAPreviews
    @Composable
    fun `FrontendScreen Improv permission rationale Bluetooth only`() {
        HAThemeForPreview {
            FrontendImprovContent(
                pendingPermissionRequest = PermissionRequest.Improv(
                    permissions = listOf(
                        android.Manifest.permission.BLUETOOTH_SCAN,
                        android.Manifest.permission.BLUETOOTH_CONNECT,
                    ),
                    showRationale = true,
                    onResult = {},
                    onDismiss = {},
                ),
            )
        }
    }

    @PreviewTest
    @HAPreviews
    @Composable
    fun `FrontendScreen Improv permission rationale Location only`() {
        HAThemeForPreview {
            FrontendImprovContent(
                pendingPermissionRequest = PermissionRequest.Improv(
                    permissions = listOf(android.Manifest.permission.ACCESS_FINE_LOCATION),
                    showRationale = true,
                    onResult = {},
                    onDismiss = {},
                ),
            )
        }
    }

    @Composable
    private fun FrontendImprovContent(
        improvUiState: ImprovUIState? = null,
        pendingPermissionRequest: PermissionRequest? = null,
    ) {
        FrontendScreenContent(
            viewState = FrontendViewState.Content(
                serverId = 1,
                url = "https://example.com",
                improvUiState = improvUiState,
            ),
            pendingPermissionRequest = pendingPermissionRequest,
            webViewClient = WebViewClient(),
            webChromeClient = WebChromeClient(),
            frontendJsCallback = FrontendJsBridge.noOp,
            onBlockInsecureRetry = {},
            onOpenExternalLink = {},
            onBlockInsecureHelpClick = {},
            onOpenSettings = {},
            onChangeSecurityLevel = {},
            onOpenLocationSettings = {},
            onConfigureHomeNetwork = { _ -> },
            onSecurityLevelHelpClick = {},
            onShowSnackbar = { _, _ -> true },
            onWebViewCreationFailed = {},
        )
    }
}
