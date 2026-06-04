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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
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
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.util.openAppSettings
import io.homeassistant.companion.android.util.compose.safeScreenHeight
import io.homeassistant.companion.android.util.compose.screenWidth
import io.homeassistant.companion.android.util.getActivity
import io.homeassistant.companion.android.util.safeTopWindowInsets
import kotlin.time.ComparableTimeMark
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

private val BarcodeScannerResultDebounce = 1500.milliseconds

// The flashlight FAB and its margin from the cutout edge.
private val FlashlightButtonSize = 48.dp
private val FlashlightButtonMargin = HADimens.SPACE2

/**
 * Translucent black tint shared by the scanner's dimming overlay (the scrim around the cutout) and
 * the flashlight FAB background, so both read as the same shade over the camera preview.
 */
private val BarcodeScannerScrimColor = Color(0xAA000000)

/**
 * Compose barcode scanner. While the camera permission is granted, renders a full-screen camera
 * preview with a centered cutout, a top-left close icon, a centered title/description block, an
 * optional alternative-action button, and (when the device has a camera flash) a flashlight FAB
 * anchored to the cutout's bottom-right. While it is missing, it renders an in-screen rationale with
 * a button to grant access instead of silently showing nothing.
 *
 * The camera preview is backed by the zxing-android-embedded [DecoratedBarcodeView] hosted in an
 * [AndroidView] and lifecycle-driven by [LifecycleResumeEffect]. Decoding runs continuously;
 * duplicate scans within 1.5 seconds are suppressed to avoid flooding the consumer (intrinsic to
 * the scanner UX, not a tunable behaviour).
 *
 * **Permission:** this Composable owns the `Manifest.permission.CAMERA` flow itself via Accompanist
 * ([rememberCameraPermissionState]). It asks on first appearance; the rationale button re-requests
 * while the system can still show the dialog and falls back to the app's settings page once the user
 * has permanently denied it. Accompanist refreshes the status on resume, so granting from settings
 * (then returning) updates the UI. Under [LocalInspectionMode] (previews) the permission is reported
 * as granted so the scanner chrome renders.
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
        onRequestPermission = {
            // After the initial request, `shouldShowRationale` is false only once the permission is
            // permanently denied — the case where re-launching would silently do nothing.
            if (status.shouldShowRationale) {
                cameraPermission.launchPermissionRequest()
            } else {
                context.openAppSettings()
            }
        },
        onResult = onResult,
        onCancel = onCancel,
        modifier = modifier,
    )
}

/**
 * Stateless scanner UI, split out of [BarcodeScanner] so both permission states can be exercised in
 * previews and screenshot tests without a real permission grant.
 *
 * When [hasCameraPermission] is true it renders the camera scanner (a black placeholder under
 * [LocalInspectionMode]); otherwise the permission rationale whose action button invokes
 * [onRequestPermission].
 *
 * @param hasCameraPermission Whether `Manifest.permission.CAMERA` is currently granted
 * @param onRequestPermission Invoked when the user taps the rationale's action button
 */
@VisibleForTesting
@Composable
internal fun BarcodeScannerContent(
    title: String,
    description: String,
    alternativeOptionLabel: String?,
    hasCameraPermission: Boolean,
    onRequestPermission: () -> Unit,
    onResult: (rawValue: String, format: BarcodeFormat) -> Unit,
    onCancel: (forAction: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val deviceHasFlashlight = remember {
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        if (hasCameraPermission) {
            val barcodeView = rememberBarcodeScannerView(onResult)
            BarcodeCameraPreview(barcodeView = barcodeView, modifier = Modifier.fillMaxSize())
            BarcodeScannerControls(
                title = title,
                description = description,
                alternativeOptionLabel = alternativeOptionLabel,
                hasFlashlight = deviceHasFlashlight && barcodeView != null,
                onCancel = onCancel,
                onToggleFlashlight = { turnOn ->
                    if (turnOn) barcodeView?.setTorchOn() else barcodeView?.setTorchOff()
                },
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

/**
 * Creates the zxing [DecoratedBarcodeView] that drives continuous scanning, or `null` in
 * `@Preview`/Robolectric ([LocalInspectionMode]) so callers can render a placeholder instead of a
 * real camera.
 *
 * The decode callback reads [onResult] through [rememberUpdatedState] so the long-lived
 * `decodeContinuous` subscription never pins a stale lambda (and, transitively, a destroyed host).
 * Duplicate scans within [BarcodeScannerResultDebounce] of the previous one are dropped.
 */
@Composable
private fun rememberBarcodeScannerView(
    onResult: (rawValue: String, format: BarcodeFormat) -> Unit,
): DecoratedBarcodeView? {
    val context = LocalContext.current
    val currentOnResult by rememberUpdatedState(onResult)

    return if (LocalInspectionMode.current) {
        null
    } else {
        remember {
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
                    if (previous != null && now - previous < BarcodeScannerResultDebounce) {
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
}

/**
 * Renders the edge-to-edge camera preview for [barcodeView], resuming/pausing it with the
 * composition lifecycle. When [barcodeView] is `null` (preview/test), renders a black placeholder
 * so the chrome drawn above stays deterministic.
 */
@Composable
private fun BarcodeCameraPreview(barcodeView: DecoratedBarcodeView?, modifier: Modifier = Modifier) {
    if (barcodeView == null) {
        Box(modifier.background(Color.Black))
        return
    }
    AndroidView(modifier = modifier, factory = { barcodeView })
    LifecycleResumeEffect(barcodeView) {
        barcodeView.resume()
        onPauseOrDispose {
            barcodeView.pause()
        }
    }
}

/**
 * The controls drawn on top of the camera preview: the cutout overlay, the close button and
 * instructions, and (when [hasFlashlight] is true) the flashlight FAB.
 */
@Composable
private fun BarcodeScannerControls(
    title: String,
    description: String,
    alternativeOptionLabel: String?,
    hasFlashlight: Boolean,
    onCancel: (forAction: Boolean) -> Unit,
    onToggleFlashlight: (Boolean) -> Unit,
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
            drawRect(BarcodeScannerScrimColor)

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

/**
 * The foreground chrome shared by the live scanner ([BarcodeScannerControls]) and the
 * permission-required fallback: a top-left close button and a centered title/description block with
 * an optional alternative-action button. Drawn over whatever sits behind it.
 *
 * @param widthFraction Fraction of the available width the instruction column occupies. Use `0.5f`
 *        in landscape so the text doesn't overlap the right-aligned cutout; `1f` when there is no
 *        cutout behind the chrome.
 */
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

/**
 * Camera-free screen shown when the camera permission is missing: the close button plus a centered
 * explanation and an action button that calls [onRequestPermission]. Reused by both the V1 activity
 * and the V2 overlay so a denied permission explains itself instead of leaving a blank scanner.
 */
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

/** Top-left close icon that cancels the scan. */
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
            TextButton(onClick = onAlternativeOption) {
                Text(text = label, color = Color.White, style = HATextStyle.Button)
            }
        }
    }
}

/**
 * Positions the [FlashlightButton] at the bottom-right corner of the cutout. In landscape the
 * cutout is pushed to the right half of the screen, so the horizontal anchor differs.
 *
 * @param containerHeightPx The height of the hosting container in pixels (from `BoxWithConstraints`)
 */
@Composable
private fun ScannerFlashlightButton(
    cutoutSize: Dp,
    screenWidthDp: Dp,
    screenHeight: Dp,
    containerHeightPx: Int,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val offsetX = if (screenWidthDp > screenHeight) {
        screenWidthDp - (0.5 * ((0.5 * screenWidthDp) - cutoutSize)) - FlashlightButtonSize - FlashlightButtonMargin
    } else {
        screenWidthDp - (0.5 * (screenWidthDp - cutoutSize)) - FlashlightButtonSize - FlashlightButtonMargin
    }
    val offsetY = with(LocalDensity.current) { (0.5 * containerHeightPx).toInt().toDp() } +
        (0.5 * cutoutSize) - FlashlightButtonSize - FlashlightButtonMargin

    // The button anchors to the cutout, which the overlay draws edge-to-edge ignoring insets, so it
    // must not apply window-inset padding itself — doing so shifts it off the cutout in landscape,
    // where the navigation bar / display cutout contributes a horizontal inset.
    FlashlightButton(
        onToggle = onToggle,
        modifier = modifier.offset(offsetX, offsetY),
    )
}

/**
 * A 48x48dp dark round button/mini FAB to toggle the device flashlight on and off. The button size
 * fits within the overlay cutout radius.
 *
 * Internal toggle state ([flashlightOn]) is saved across configuration changes via [rememberSaveable].
 */
@Composable
private fun FlashlightButton(onToggle: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    var flashlightOn by rememberSaveable { mutableStateOf(false) }
    OutlinedButton(
        modifier = modifier.size(FlashlightButtonSize),
        shape = CircleShape,
        border = null,
        contentPadding = PaddingValues(0.dp),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = BarcodeScannerScrimColor),
        onClick = {
            val next = !flashlightOn
            flashlightOn = next
            onToggle(next)
        },
    ) {
        Icon(
            imageVector = if (flashlightOn) Icons.Default.FlashlightOff else Icons.Default.FlashlightOn,
            contentDescription = stringResource(commonR.string.toggle_flashlight),
            tint = Color.White,
        )
    }
}
