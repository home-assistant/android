package io.homeassistant.companion.android.barcode.view

import androidx.appcompat.view.ContextThemeWrapper
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.Button
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.isVisible
import com.journeyapps.barcodescanner.CaptureManager
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.util.getActivity

@Composable
fun BarcodeScannerView(
    hasPermission: Boolean,
    requestPermission: () -> Unit,
    onResult: (String) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val barcodeView = remember {
        val style = R.style.HomeAssistantBarcodeScanner
        DecoratedBarcodeView(ContextThemeWrapper(context, style)).apply {
            val activity = context.getActivity() ?: return@apply
            statusView.isVisible = false // Hide default UI

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

    Box {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { barcodeView }
        )
        if (hasPermission) {
            DisposableEffect("barcodeView") {
                barcodeView.resume()
                onDispose {
                    barcodeView.pause()
                }
            }
        }
        // TODO https://stackoverflow.com/a/73533699/4214819

        Column(modifier = Modifier.safeDrawingPadding()) {
            IconButton(onClick = onCancel) {
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
                Button(onClick = requestPermission) {
                    Text(text = "Toggle flashlight")
                }
            }
        }
    }
}
