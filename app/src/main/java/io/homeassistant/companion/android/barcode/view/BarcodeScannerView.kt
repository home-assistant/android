package io.homeassistant.companion.android.barcode.view

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.material.Button
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.isVisible
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.journeyapps.barcodescanner.CaptureManager
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.util.getActivity

@Composable
fun BarcodeScannerView(
    title: String,
    subtitle: String,
    action: String?,
    hasPermission: Boolean,
    requestPermission: () -> Unit,
    onResult: (String) -> Unit,
    onCancel: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val barcodeView = remember {
        DecoratedBarcodeView(context).apply {
            val activity = context.getActivity() ?: return@apply
            // Hide default UI
            viewFinder.isVisible = false
            statusView.isVisible = false

            val captureManager = CaptureManager(activity, this)
            captureManager.initializeFromIntent(null, null)
            captureManager.decode()
            decodeContinuous { result ->
                result.text.ifBlank { null }?.let {
                    onResult(it)
                }
            }
        }
    }
    var flashlightOn by remember { mutableStateOf(false) }

    // Main screen structure:
    // - Starting with composables that should go edge to edge (background)
    // - Box with overlay fitting inside insets for main contents/controls
    Box {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { barcodeView }
        )
        if (hasPermission) {
            LifecycleResumeEffect("barcodeView") {
                barcodeView.resume()
                onPauseOrDispose {
                    barcodeView.pause()
                }
            }
        }
        Spacer(
            modifier = Modifier
                .background(barcodeScannerOverlayColor) // matching overlay
                .fillMaxWidth()
                .windowInsetsTopHeight(WindowInsets.systemBars)
        )
        Spacer(
            modifier = Modifier
                .background(barcodeScannerOverlayColor) // matching overlay
                .fillMaxWidth()
                .windowInsetsBottomHeight(WindowInsets.systemBars)
                .align(Alignment.BottomStart)
        )

        Box(
            modifier = Modifier
                .safeDrawingPadding()
                .fillMaxSize()
        ) {
            val configuration = LocalConfiguration.current
            val systemBarsPadding = WindowInsets.systemBars.asPaddingValues()
            val screenHeight = configuration.screenHeightDp.dp - systemBarsPadding.calculateTopPadding() - systemBarsPadding.calculateBottomPadding()
            val screenWidth = configuration.screenWidthDp.dp
            val cutoutSize = minOf(minOf(screenHeight, screenWidth) - 48.dp, 320.dp)

            BarcodeScannerOverlay(modifier = Modifier.fillMaxSize(), cutout = cutoutSize)
            Column {
                IconButton(onClick = { onCancel(false) }) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(commonR.string.cancel),
                        tint = Color.White
                    )
                }
                Row {
                    Button(onClick = requestPermission) {
                        Text(text = "Grant camera permission")
                    }
                    Button(onClick = {
                        if (flashlightOn) {
                            barcodeView.setTorchOff()
                        } else {
                            barcodeView.setTorchOn()
                        }
                        flashlightOn = !flashlightOn
                    }) {
                        Text(text = "Toggle flashlight")
                    }
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth(if (screenWidth > screenHeight) 0.5f else 1f)
                        .padding(horizontal = 16.dp)
                        .padding(top = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = title,
                        color = Color.White,
                        style = MaterialTheme.typography.h6
                    )
                    Text(
                        text = subtitle,
                        color = Color.White,
                        style = MaterialTheme.typography.subtitle1
                    )
                    action?.let {
                        Spacer(Modifier.height(32.dp))
                        TextButton(onClick = { onCancel(true) }) {
                            Text(it)
                        }
                    }
                }
            }
        }
    }
}
