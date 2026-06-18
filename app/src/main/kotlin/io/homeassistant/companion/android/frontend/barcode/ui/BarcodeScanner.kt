package io.homeassistant.companion.android.frontend.barcode.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.times
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.isVisible
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.CaptureManager
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.composable.HAAccentButton
import io.homeassistant.companion.android.common.compose.composable.HAPlainButton
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.util.openSystemAppSettings
import io.homeassistant.companion.android.util.compose.safeScreenHeight
import io.homeassistant.companion.android.util.compose.screenWidth
import io.homeassistant.companion.android.util.getActivity
import io.homeassistant.companion.android.util.safeTopWindowInsets
import kotlin.time.ComparableTimeMark
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

private val SCANNER_RESULT_DEBOUNCE = 1.5.seconds
private val FLASHLIGHT_BUTTON_SIZE = HADimens.SPACE12
private val FLASHLIGHT_BUTTON_MARGIN = HADimens.SPACE2

/**
 * Translucent black tint shared by the scanner's dimming overlay (the scrim around the cutout) and
 * the flashlight FAB background, so both read as the same shade over the camera preview.
 */
private val SCRIM_COLOR = Color(0xAA000000)

/**
 * Compose barcode scanner. While the camera permission is granted, renders a full-screen camera
 * preview with a centered cutout, a top-left close icon, a centered title/description block, an
 * optional alternative-action button, and (when the device has a camera flash) a flashlight FAB
 * anchored to the cutout's bottom-right. While it is missing, it renders an in-screen rationale with
 * a button to grant access instead of silently showing nothing.
 *
 * The camera preview is backed by the zxing-android-embedded [DecoratedBarcodeView].
 * Decoding runs continuously; duplicate scans within 1.5 seconds are suppressed to avoid flooding
 * the consumer (intrinsic to the scanner UX, not a tunable behaviour).
 *
 * **Permission:** this Composable owns the `Manifest.permission.CAMERA`. It asks on first appearance;
 * the rationale button re-requests while the system can still show the dialog and falls back
 * to the app's settings page once the user has permanently denied it.
 *
 * @param title Bold header centered above the cutout
 * @param description Subtitle below [title]
 * @param alternativeOptionLabel Optional "Enter manually" / "Skip" button label below the
 *        description; when null, no button is shown
 * @param onResult Called with the decoded text + zxing [BarcodeFormat] when a barcode is scanned.
 *        The caller maps the format to a frontend wire string.
 * @param onCancel Called when the user cancels. `forAction = true` means the user picked the
 *        alternative-action button; `forAction = false` means the user tapped the close icon.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun BarcodeScanner(
    title: String,
    description: String,
    alternativeOptionLabel: String?,
    onResult: (rawValue: String, format: BarcodeFormat) -> Unit,
    onCancel: (forAction: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val cameraPermission = rememberPermissionState(Manifest.permission.CAMERA)
    val deviceHasFlashlight = remember {
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)
    }
    var flashlightOn by remember { mutableStateOf(false) }
    val status = cameraPermission.status

    LaunchedEffect(Unit) {
        if (!status.isGranted) {
            cameraPermission.launchPermissionRequest()
        }
    }

    BarcodeScannerContent(
        title = title,
        description = description,
        alternativeOptionLabel = alternativeOptionLabel,
        hasCameraPermission = status.isGranted,
        hasFlashlight = deviceHasFlashlight,
        flashlightOn = flashlightOn,
        onRequestPermission = {
            // After the initial request, `shouldShowRationale` is false only once the permission is
            // permanently denied — the case where re-launching would silently do nothing.
            if (status.shouldShowRationale) {
                cameraPermission.launchPermissionRequest()
            } else {
                context.openSystemAppSettings()
            }
        },
        onResult = onResult,
        onToggleFlashlight = {
            flashlightOn = !flashlightOn
        },
        onCancel = onCancel,
        modifier = modifier,
    )
}

@VisibleForTesting
@Composable
internal fun BarcodeScannerContent(
    title: String,
    description: String,
    alternativeOptionLabel: String?,
    hasCameraPermission: Boolean,
    hasFlashlight: Boolean,
    flashlightOn: Boolean,
    onRequestPermission: () -> Unit,
    onResult: (rawValue: String, format: BarcodeFormat) -> Unit,
    onToggleFlashlight: () -> Unit,
    onCancel: (forAction: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        if (hasCameraPermission) {
            BarcodeCameraPreview(flashlightOn = flashlightOn, onResult = onResult, modifier = Modifier.fillMaxSize())
            BarcodeScannerControls(
                title = title,
                description = description,
                alternativeOptionLabel = alternativeOptionLabel,
                hasFlashlight = hasFlashlight,
                flashlightOn = flashlightOn,
                onCancel = onCancel,
                onToggleFlashlight = onToggleFlashlight,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            BarcodeScannerPermissionRequired(
                onClose = { onCancel(false) },
                onRequestPermission = onRequestPermission,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun BarcodeCameraPreview(
    flashlightOn: Boolean,
    onResult: (rawValue: String, format: BarcodeFormat) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (LocalInspectionMode.current) {
        Box(modifier = modifier.background(Color.White))
        return
    }

    val view = rememberBarcodeScannerView(onResult)

    AndroidView(modifier = modifier, factory = { view })

    LifecycleResumeEffect(view) {
        view.resume()
        onPauseOrDispose {
            view.pause()
        }
    }

    SideEffect {
        if (flashlightOn) {
            view.setTorchOn()
        } else {
            view.setTorchOff()
        }
    }
}

@Composable
private fun rememberBarcodeScannerView(
    onResult: (rawValue: String, format: BarcodeFormat) -> Unit,
): DecoratedBarcodeView {
    val context = LocalContext.current
    val currentOnResult by rememberUpdatedState(onResult)

    return remember {
        var lastScan: ComparableTimeMark? = null

        DecoratedBarcodeView(context).apply {
            val activity = context.getActivity() ?: return@apply
            // Hide the library's default UI; the cutout overlay and chrome are drawn above.
            viewFinder.isVisible = false
            statusView.isVisible = false

            val captureManager = CaptureManager(activity, this)
            captureManager.initializeFromIntent(null, null)
            captureManager.decode()
            decodeContinuous { result ->
                val now = TimeSource.Monotonic.markNow()
                val previous = lastScan
                if (previous != null && now - previous < SCANNER_RESULT_DEBOUNCE) {
                    return@decodeContinuous
                }
                result.text.ifBlank { null }?.let {
                    lastScan = now
                    currentOnResult(it, result.barcodeFormat)
                }
            }
        }
    }
}

@Composable
private fun BarcodeScannerControls(
    title: String,
    description: String,
    alternativeOptionLabel: String?,
    hasFlashlight: Boolean,
    flashlightOn: Boolean,
    onCancel: (forAction: Boolean) -> Unit,
    onToggleFlashlight: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier) {
        val screenHeight = safeScreenHeight()
        val screenWidthDp = screenWidth()
        val cutoutSize = minOf(minOf(screenHeight, screenWidthDp) - 48.dp, 320.dp)

        BarcodeScannerOverlay(modifier = Modifier.fillMaxSize(), cutout = cutoutSize)

        BarcodeScannerChrome(
            title = title,
            description = description,
            alternativeOptionLabel = alternativeOptionLabel,
            onCancel = onCancel,
            // Narrow the instructions to half-width in landscape so they don't overlap the cutout.
            widthFraction = if (screenWidthDp > screenHeight) 0.5f else 1f,
        )

        if (hasFlashlight) {
            ScannerFlashlightButton(
                flashlightOn = flashlightOn,
                cutoutSize = cutoutSize,
                screenWidthDp = screenWidthDp,
                screenHeight = screenHeight,
                containerHeightPx = constraints.maxHeight,
                onToggle = onToggleFlashlight,
            )
        }
    }
}

/**
 * A semi-transparent overlay with a rounded square cutout in the middle (portrait) or on
 * the right half (landscape), to use as a QR code viewfinder for the scanner's camera.
 * Based on https://stackoverflow.com/a/73533699/4214819.
 */
@Composable
private fun BarcodeScannerOverlay(cutout: Dp, modifier: Modifier = Modifier) {
    val widthInPx: Float
    val heightInPx: Float
    val cornerInPx: Float

    with(LocalDensity.current) {
        widthInPx = cutout.toPx()
        heightInPx = cutout.toPx()
        cornerInPx = 28.dp.toPx() // Material 3 extra large rounding
    }

    Canvas(modifier = modifier) {
        val canvasWidth = size.width
        val canvasHeight = size.height

        with(drawContext.canvas.nativeCanvas) {
            val checkPoint = saveLayer(null, null)

            // Destination
            drawRect(SCRIM_COLOR)

            // Source
            drawRoundRect(
                topLeft = Offset(
                    x = if (canvasWidth > canvasHeight) {
                        (canvasWidth / 2) + (((canvasWidth / 2) - widthInPx) / 2)
                    } else {
                        (canvasWidth - widthInPx) / 2
                    },
                    y = (canvasHeight - heightInPx) / 2,
                ),
                size = Size(widthInPx, heightInPx),
                cornerRadius = CornerRadius(cornerInPx, cornerInPx),
                color = Color.Transparent,
                blendMode = BlendMode.Clear,
            )
            restoreToCount(checkPoint)
        }
    }
}

@Composable
private fun BarcodeScannerChrome(
    title: String,
    description: String,
    alternativeOptionLabel: String?,
    onCancel: (forAction: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    widthFraction: Float = 1f,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(safeTopWindowInsets()),
    ) {
        ScannerCloseButton(onClose = { onCancel(false) })
        ScannerInstructions(
            title = title,
            description = description,
            alternativeOptionLabel = alternativeOptionLabel,
            widthFraction = widthFraction,
            onAlternativeOption = { onCancel(true) },
        )
    }
}

@Composable
private fun BarcodeScannerPermissionRequired(
    onClose: () -> Unit,
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(safeTopWindowInsets()),
    ) {
        ScannerCloseButton(onClose = onClose)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = HADimens.SPACE4),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(commonR.string.barcode_camera_permission_title),
                color = Color.White,
                style = HATextStyle.Headline,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(HADimens.SPACE2))
            Text(
                text = stringResource(commonR.string.barcode_camera_permission_message),
                color = Color.White,
                style = HATextStyle.Body,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(HADimens.SPACE6))
            HAAccentButton(
                text = stringResource(commonR.string.barcode_camera_permission_action),
                onClick = onRequestPermission,
            )
        }
    }
}

@Composable
private fun ScannerCloseButton(onClose: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth()) {
        IconButton(onClick = onClose) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = stringResource(commonR.string.cancel),
                tint = Color.White,
            )
        }
    }
}

/**
 * Centered title/description block with an optional alternative-action button. [widthFraction]
 * narrows the column to half-width in landscape so it doesn't overlap the right-aligned cutout.
 */
@Composable
private fun ScannerInstructions(
    title: String,
    description: String,
    alternativeOptionLabel: String?,
    widthFraction: Float,
    onAlternativeOption: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth(widthFraction)
            .padding(horizontal = HADimens.SPACE4)
            .padding(top = HADimens.SPACE8),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = title, color = Color.White, style = HATextStyle.Headline)
        Text(text = description, color = Color.White, style = HATextStyle.Body)

        alternativeOptionLabel?.let { label ->
            Spacer(Modifier.height(HADimens.SPACE8))
            HAPlainButton(text = label, onClick = onAlternativeOption)
        }
    }
}

/**
 * Positions the [FlashlightButton] in the bottom-right corner of the cutout.
 */
@Composable
private fun ScannerFlashlightButton(
    flashlightOn: Boolean,
    cutoutSize: Dp,
    screenWidthDp: Dp,
    screenHeight: Dp,
    containerHeightPx: Int,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val offsetX = if (screenWidthDp > screenHeight) {
        screenWidthDp - (0.5 * ((0.5 * screenWidthDp) - cutoutSize)) - FLASHLIGHT_BUTTON_SIZE - FLASHLIGHT_BUTTON_MARGIN
    } else {
        screenWidthDp - (0.5 * (screenWidthDp - cutoutSize)) - FLASHLIGHT_BUTTON_SIZE - FLASHLIGHT_BUTTON_MARGIN
    }
    val offsetY = with(LocalDensity.current) { (0.5 * containerHeightPx).toInt().toDp() } +
        (0.5 * cutoutSize) - FLASHLIGHT_BUTTON_SIZE - FLASHLIGHT_BUTTON_MARGIN

    FlashlightButton(
        flashlightOn = flashlightOn,
        onToggle = onToggle,
        modifier = modifier.offset(offsetX, offsetY),
    )
}

@Composable
private fun FlashlightButton(flashlightOn: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(
        modifier = modifier.size(FLASHLIGHT_BUTTON_SIZE),
        shape = CircleShape,
        border = null,
        contentPadding = PaddingValues(0.dp),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = SCRIM_COLOR),
        onClick = onToggle,
    ) {
        Icon(
            imageVector = if (flashlightOn) Icons.Default.FlashlightOff else Icons.Default.FlashlightOn,
            contentDescription = stringResource(commonR.string.toggle_flashlight),
            tint = Color.White,
        )
    }
}
