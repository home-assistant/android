package io.homeassistant.companion.android.barcode.view

import android.annotation.SuppressLint
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
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Scaffold
import androidx.compose.material.SnackbarDuration
import androidx.compose.material.SnackbarHost
import androidx.compose.material.SnackbarResult
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.rememberScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.times
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.isVisible
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.CaptureManager
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.util.compose.darkColorBackground
import io.homeassistant.companion.android.util.compose.safeScreenHeight
import io.homeassistant.companion.android.util.compose.screenWidth
import io.homeassistant.companion.android.util.getActivity
import io.homeassistant.companion.android.util.safeBottomWindowInsets
import io.homeassistant.companion.android.util.safeTopWindowInsets

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun BarcodeScannerView(
    title: String,
    subtitle: String,
    action: String?,
    hasFlashlight: Boolean,
    hasPermission: Boolean,
    didRequestPermission: Boolean,
    requestPermission: () -> Unit,
    onResult: (String, BarcodeFormat) -> Unit,
    onCancel: (Boolean) -> Unit,
    resultTimeoutMillis: Long = 1500L,
) {
    val context = LocalContext.current
    val barcodeView = remember {
        // The app remembers last time scanned to prevent spamming the frontend
        var resultScanned = System.currentTimeMillis()

        DecoratedBarcodeView(context).apply {
            val activity = context.getActivity() ?: return@apply
            // Hide default UI
            viewFinder.isVisible = false
            statusView.isVisible = false

            val captureManager = CaptureManager(activity, this)
            captureManager.initializeFromIntent(null, null)
            captureManager.decode()
            decodeContinuous { result ->
                if ((System.currentTimeMillis() - resultScanned) < resultTimeoutMillis) return@decodeContinuous
                result.text.ifBlank { null }?.let {
                    resultScanned = System.currentTimeMillis()
                    onResult(it, result.barcodeFormat)
                }
            }
        }
    }

    // Main screen structure:
    // - Starting with composables that should go edge to edge (background)
    // - Box with overlay fitting inside insets for main contents/controls
    Box {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { barcodeView },
        )
        if (hasPermission) {
            LifecycleResumeEffect("barcodeView") {
                barcodeView.resume()
                onPauseOrDispose {
                    barcodeView.pause()
                }
            }
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize(),
        ) {
            val screenHeight = safeScreenHeight()
            val screenWidth = screenWidth()

            val cutoutSize = minOf(minOf(screenHeight, screenWidth) - 48.dp, 320.dp)

            val scaffoldState = rememberScaffoldState()
            if (!hasPermission && didRequestPermission) {
                LaunchedEffect("permission") {
                    scaffoldState.snackbarHostState.showSnackbar(
                        context.getString(commonR.string.missing_camera_permission),
                        context.getString(commonR.string.settings),
                        SnackbarDuration.Indefinite,
                    ).let { result ->
                        if (result == SnackbarResult.ActionPerformed) {
                            requestPermission()
                        }
                    }
                }
            }

            BarcodeScannerOverlay(modifier = Modifier.fillMaxSize(), cutout = cutoutSize)
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
                        title = {},
                        navigationIcon = {
                            IconButton(onClick = { onCancel(false) }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = stringResource(commonR.string.cancel),
                                    tint = Color.White,
                                )
                            }
                        },
                        backgroundColor = Color.Transparent,
                        windowInsets = safeTopWindowInsets(),
                        elevation = 0.dp,
                    )
                },
                backgroundColor = Color.Transparent,
            ) { paddingValues ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth(if (screenWidth > screenHeight) 0.5f else 1f)
                        .padding(paddingValues)
                        .padding(horizontal = 16.dp)
                        .padding(top = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = title,
                        color = Color.White,
                        style = MaterialTheme.typography.h6,
                    )
                    Text(
                        text = subtitle,
                        color = Color.White,
                        style = MaterialTheme.typography.subtitle1,
                    )
                    action?.let {
                        Spacer(Modifier.height(32.dp))
                        TextButton(onClick = { onCancel(true) }) {
                            Text(it)
                        }
                    }
                }
            }
            if (hasFlashlight && hasPermission) {
                // Align to bottom right of cutout - button size - margin
                // Note in landscape that the cutout is pushed to the right half of the screen
                val offsetX = if (screenWidth > screenHeight) {
                    screenWidth - (0.5 * ((0.5 * screenWidth) - cutoutSize)) - 48.dp - 8.dp
                } else {
                    screenWidth - (0.5 * (screenWidth - cutoutSize)) - 48.dp - 8.dp
                }
                val offsetY = with(LocalDensity.current) {
                    (0.5 * constraints.maxHeight).toInt().toDp()
                } + (0.5 * cutoutSize) - 48.dp - 8.dp
                FlashlightButton(
                    onToggle = { turnOn ->
                        if (turnOn) {
                            barcodeView.setTorchOn()
                        } else {
                            barcodeView.setTorchOff()
                        }
                    },
                    modifier = Modifier.offset(offsetX, offsetY),
                )
            }
        }
    }
}

/**
 * A 48x48dp dark round button/mini FAB to toggle the flashlight on the device on and off.
 * The button size fits with the overlay radius.
 */
@Composable
fun FlashlightButton(onToggle: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    var flashlightOn by rememberSaveable { mutableStateOf(false) }
    OutlinedButton(
        modifier = modifier.size(48.dp),
        elevation = ButtonDefaults.elevation(),
        shape = CircleShape,
        border = null,
        contentPadding = PaddingValues(0.dp),
        colors = ButtonDefaults.outlinedButtonColors(backgroundColor = darkColorBackground),
        onClick = {
            onToggle(!flashlightOn)
            flashlightOn = !flashlightOn
        },
    ) {
        Icon(
            imageVector = if (flashlightOn) Icons.Default.FlashlightOff else Icons.Default.FlashlightOn,
            contentDescription = stringResource(commonR.string.toggle_flashlight),
            tint = Color.White,
        )
    }
}
