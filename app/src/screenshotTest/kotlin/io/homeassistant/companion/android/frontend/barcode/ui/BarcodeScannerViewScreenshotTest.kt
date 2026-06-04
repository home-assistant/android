package io.homeassistant.companion.android.frontend.barcode.ui

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.util.compose.HAPreviews

class BarcodeScannerViewScreenshotTest {

    @PreviewTest
    @HAPreviews
    @Composable
    fun `BarcodeScanner with flashlight and action`() {
        HAThemeForPreview {
            BarcodeScannerContent(
                title = "Scan code",
                description = "Point the camera at the code to scan it",
                alternativeOptionLabel = "Enter manually",
                hasCameraPermission = true,
                onRequestPermission = {},
                onResult = { _, _ -> },
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
                onRequestPermission = {},
                onResult = { _, _ -> },
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
                onRequestPermission = {},
                onResult = { _, _ -> },
                onCancel = {},
            )
        }
    }
}
