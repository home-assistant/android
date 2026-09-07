package io.homeassistant.companion.android.home.views

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.IconButtonDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import androidx.wear.tooling.preview.devices.WearDevices
import io.homeassistant.companion.android.common.R
import io.homeassistant.companion.android.theme.WearAppTheme
import io.homeassistant.companion.android.theme.wearColorScheme
import io.homeassistant.companion.android.util.onEntityFeedback
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.sqrt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorPicker(
    onColorChanged: (Int) -> Unit,
    onNavigateToCustom: () -> Unit,
    isToastEnabled: Boolean,
    isHapticEnabled: Boolean,
) {
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current

    val presets = listOf( // Wish there was a way to access favorite colors via API
        Color(0xFFEF9A9A), // Red
        Color(0xFFA5D6A7), // Green
        Color(0xFF90CAF9), // Blue
        Color(0xFFCB9AEF), // Purple
        Color(0xFFEFCF9A), // Orange
        Color(0xFFFFFFFF), // White

    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            maxItemsInEachRow = 3,
        ) {
            presets.forEach { color ->
                ColorCircle(color = color) {
                    onColorChanged(color.toArgb())
                    onEntityFeedback(
                        isToastEnabled,
                        isHapticEnabled,
                        context.getString(R.string.color_changed),
                        context,
                        haptic,
                    )
                }
            }
        }
        Button(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            onClick = onNavigateToCustom,
        ) {
            Text(stringResource(R.string.custom))
        }
    }
}

@Composable
fun ColorCircle(
    color: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .padding(4.dp)
            .size(40.dp)
            .clip(CircleShape)
            .background(color)
            .border(2.dp, wearColorScheme.outline, CircleShape)
            .clickable { onClick() },
    )
}

@Composable
fun CustomColorPicker(
    onColorChanged: (Int) -> Unit,
    onBack: () -> Unit,
    isToastEnabled: Boolean,
    isHapticEnabled: Boolean,
) {
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val density = LocalDensity.current
    var currentColor by remember { mutableStateOf(Color.White) }
    val colorChangedMsg = stringResource(R.string.color_changed)

    var fingerPosition by remember { mutableStateOf<Offset?>(null) }
    var wheelCenter by remember { mutableStateOf(Offset.Zero) }
    var wheelRadius by remember { mutableFloatStateOf(0f) }
    var cancelBtnBounds by remember { mutableStateOf(Rect.Zero) }

    val isFingerOverCancel = remember(fingerPosition, cancelBtnBounds) {
        fingerPosition?.let { cancelBtnBounds.contains(it) } ?: false
    }
    val cancelBtnAlpha by animateFloatAsState(
        targetValue = if (isFingerOverCancel) 0f else 1f,
        label = "cancelBtnAlpha",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { fingerPosition = it },
                    onDragEnd = { fingerPosition = null },
                    onDragCancel = { fingerPosition = null },
                    onDrag = { change, _ ->
                        fingerPosition = change.position
                        val color = getColorAtOffset(change.position, wheelCenter, wheelRadius)
                        if (color != null) {
                            currentColor = color
                        }
                        change.consume()
                    },
                )
            }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val okButtonRadius = with(density) { 32.dp.toPx() } // Half of 64dp
                    val dx = offset.x - wheelCenter.x
                    val dy = offset.y - wheelCenter.y
                    val distance = sqrt(dx * dx + dy * dy)

                    if (distance <= okButtonRadius) {
                        onColorChanged(currentColor.toArgb())
                        onEntityFeedback(
                            isToastEnabled,
                            isHapticEnabled,
                            colorChangedMsg,
                            context,
                            haptic,
                        )
                    } else if (cancelBtnBounds.contains(offset)) {
                        onBack()
                    } else {
                        val color = getColorAtOffset(offset, wheelCenter, wheelRadius)
                        if (color != null) {
                            currentColor = color
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(200.dp)
                .padding(4.dp)
                .onGloballyPositioned {
                    wheelCenter = it.boundsInRoot().center
                    wheelRadius = it.size.width / 2f
                },
            contentAlignment = Alignment.Center,
        ) {
            ColorWheel(modifier = Modifier.fillMaxSize())
            // Confirm button in the middle
            Box(
                modifier = Modifier
                    .size(64.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(currentColor)
                        .border(2.dp, Color.White, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        tint = if (currentColor.isLight()) Color.Black else Color.White,
                        contentDescription = stringResource(R.string.ok),
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }
        // Small cancel button overlayed at the bottom
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 12.dp)
                .height(IconButtonDefaults.SmallButtonSize)
                .wrapContentWidth()
                .alpha(cancelBtnAlpha)
                .onGloballyPositioned {
                    cancelBtnBounds = it.boundsInRoot()
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .height(28.dp)
                    .padding(horizontal = 8.dp)
                    .clip(CircleShape)
                    .background(wearColorScheme.surfaceContainer.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.cancel),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
    }
}

@Composable
fun ColorWheel(
    modifier: Modifier = Modifier,
) {
    val hueGradient = remember {
        Brush.sweepGradient(
            listOf(
                Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red,
            ),
        )
    }

    val saturationGradient = remember {
        Brush.radialGradient(
            colors = listOf(Color.White, Color.Transparent),
        )
    }

    Canvas(modifier = modifier) {
        val center = size.center
        val radius = size.minDimension / 2

        drawCircle(brush = hueGradient, radius = radius, center = center)
        drawCircle(brush = saturationGradient, radius = radius, center = center)
    }
}

private fun getColorAtOffset(offset: Offset, center: Offset, radius: Float): Color? {
    val dx = offset.x - center.x
    val dy = offset.y - center.y
    val distance = sqrt(dx * dx + dy * dy)

    if (distance > radius) return null

    val angle = atan2(dy, dx)
    var hue = angle * 180f / PI.toFloat()
    if (hue < 0) hue += 360f

    val saturation = (distance / radius).coerceIn(0f, 1f)

    return Color.hsv(hue, saturation, 1f)
}

private fun Color.isLight(): Boolean {
    val luminance = 0.299 * red + 0.587 * green + 0.114 * blue
    return luminance > 0.5
}

@Preview(device = WearDevices.LARGE_ROUND)
@Composable
fun PreviewColorPicker() {
    WearAppTheme {
        Box(modifier = Modifier.background(Color.Black)) {
            CustomColorPicker(
                onColorChanged = {},
                onBack = {},
                isToastEnabled = false,
                isHapticEnabled = false,
            )
        }
    }
}
