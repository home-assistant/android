package io.homeassistant.companion.android.frontend.barcode.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.util.compose.HAPreviews

class BarcodeScannerScreenshotTest {

    @PreviewTest
    @HAPreviews
    @Composable
    fun `BarcodeScanner with flashlight OFF and action`() {
        HAThemeForPreview {
            BarcodeScannerContent(
                title = "Scan code",
                description = "Point the camera at the code to scan it",
                alternativeOptionLabel = "Enter manually",
                hasCameraPermission = true,
                hasFlashlight = true,
                flashlightOn = false,
                onRequestPermission = {},
                onResult = { _, _ -> },
                onToggleFlashlight = {},
                onCancel = {},
            )
        }
    }

    @PreviewTest
    @Preview // Only one screenshot just to test the toggle of the flashlight
    @Composable
    fun `BarcodeScanner with flashlight ON and action`() {
        HAThemeForPreview {
            BarcodeScannerContent(
                title = "Scan code",
                description = "Point the camera at the code to scan it",
                alternativeOptionLabel = "Enter manually",
                hasCameraPermission = true,
                hasFlashlight = true,
                flashlightOn = true,
                onRequestPermission = {},
                onResult = { _, _ -> },
                onToggleFlashlight = {},
                onCancel = {},
            )
        }
    }

    @PreviewTest
    @HAPreviews
    @Composable
    fun `BarcodeScanner without flashlight without action`() {
        HAThemeForPreview {
            BarcodeScannerContent(
                title = "Scan code",
                description = "Point the camera at the code to scan it",
                alternativeOptionLabel = null,
                hasCameraPermission = true,
                hasFlashlight = false,
                flashlightOn = false,
                onRequestPermission = {},
                onResult = { _, _ -> },
                onToggleFlashlight = {},
                onCancel = {},
            )
        }
    }

    @PreviewTest
    @HAPreviews
    @Composable
    fun `BarcodeScanner camera permission denied`() {
        HAThemeForPreview {
            BarcodeScannerContent(
                title = "Scan code",
                description = "Point the camera at the code to scan it",
                alternativeOptionLabel = null,
                hasCameraPermission = false,
                hasFlashlight = true,
                flashlightOn = false,
                onRequestPermission = {},
                onResult = { _, _ -> },
                onToggleFlashlight = {},
                onCancel = {},
            )
        }
    }
}
