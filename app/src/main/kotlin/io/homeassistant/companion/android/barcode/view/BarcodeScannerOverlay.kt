package io.homeassistant.companion.android.barcode.view

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A semi-transparent overlay with a rounded square cutout in the middle (portrait) or on
 * the right half (landscape), to use as a QR code viewfinder for the scanner's camera.
 * Based on https://stackoverflow.com/a/73533699/4214819.
 */
@Composable
fun BarcodeScannerOverlay(modifier: Modifier, cutout: Dp) {
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
            drawRect(barcodeScannerOverlayColor)

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

private val barcodeScannerOverlayColor = Color(0xAA000000)
