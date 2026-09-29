package io.homeassistant.companion.android.util.compose

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Adds an interactive vertical scrollbar to a composable with a [LazyListState].
 *
 * @param lazyListState State of the scrollbar list.
 * @param width Width of the scrollbar thumb indicator.
 * @param touchWidth Width of the touchable area along the right edge for drag interaction.
 * @param color Color of the scrollbar thumb. Defaults to [LocalHAColorScheme]'s colorBorderNeutralNormal.
 * @param estimatedItemHeight Estimated height per item to maintain a stable scrollbar thumb size.
 * @param minThumbHeight Minimum height of the scrollbar thumb.
 */
@Composable
fun Modifier.verticalScrollBar(
    lazyListState: LazyListState,
    width: Dp = 4.dp,
    touchWidth: Dp = 36.dp,
    color: Color = LocalHAColorScheme.current.colorBorderNeutralNormal,
    estimatedItemHeight: Dp = 64.dp,
    minThumbHeight: Dp = 36.dp,
): Modifier {
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()

    val widthPx = with(density) { width.toPx() }
    val touchWidthPx = with(density) { touchWidth.toPx() }
    val minThumbHeightPx = with(density) { minThumbHeight.toPx() }
    val estimatedItemHeightPx = with(density) { estimatedItemHeight.toPx() }

    var isDragging by remember { mutableStateOf(false) }
    val isScrolling = lazyListState.isScrollInProgress

    val alpha = remember { Animatable(0f) }

    val canScroll by remember {
        derivedStateOf { checkCanScroll(lazyListState) }
    }

    val totalItemsCount by remember {
        derivedStateOf { lazyListState.layoutInfo.totalItemsCount }
    }

    LaunchedEffect(isScrolling, isDragging, canScroll) {
        if (canScroll && (isScrolling || isDragging)) {
            alpha.snapTo(1f)
        } else {
            delay(1.seconds)
            alpha.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 300),
            )
        }
    }

    LaunchedEffect(totalItemsCount) {
        if (canScroll) {
            alpha.snapTo(1f)
            delay(1.seconds)
            alpha.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 300),
            )
        }
    }

    return this
        .scrollbarDragInput(
            lazyListState = lazyListState,
            canScroll = canScroll,
            touchWidthPx = touchWidthPx,
            estimatedItemHeightPx = estimatedItemHeightPx,
            minThumbHeightPx = minThumbHeightPx,
            coroutineScope = coroutineScope,
            onDraggingChanged = { isDragging = it },
        )
        .drawScrollBar(
            lazyListState = lazyListState,
            canScroll = canScroll,
            alpha = alpha.value,
            color = color,
            widthPx = widthPx,
            estimatedItemHeightPx = estimatedItemHeightPx,
            minThumbHeightPx = minThumbHeightPx,
        )
}

private fun checkCanScroll(lazyListState: LazyListState): Boolean {
    val layoutInfo = lazyListState.layoutInfo
    val totalItems = layoutInfo.totalItemsCount
    val visibleItems = layoutInfo.visibleItemsInfo
    if (totalItems == 0 || visibleItems.isEmpty()) {
        return false
    }
    return visibleItems.size < totalItems ||
        lazyListState.firstVisibleItemScrollOffset > 0 ||
        visibleItems.last().size > layoutInfo.viewportSize.height - visibleItems.last().offset
}

private data class ScrollBarMetrics(
    val thumbHeight: Float,
    val thumbOffsetY: Float,
    val maxThumbOffsetY: Float,
)

private fun calculateScrollBarMetrics(
    lazyListState: LazyListState,
    viewportHeight: Float,
    estimatedItemHeightPx: Float,
    minThumbHeightPx: Float,
): ScrollBarMetrics? {
    val currentTotalItems = lazyListState.layoutInfo.totalItemsCount
    if (currentTotalItems == 0) return null

    val totalEstimatedHeight = currentTotalItems * estimatedItemHeightPx
    val maxScrolledOffset = (totalEstimatedHeight - viewportHeight).coerceAtLeast(1f)
    val scrolledOffset =
        lazyListState.firstVisibleItemIndex * estimatedItemHeightPx +
            lazyListState.firstVisibleItemScrollOffset
    val scrollProgress = (scrolledOffset / maxScrolledOffset).coerceIn(0f, 1f)

    val viewportRatio = (viewportHeight / totalEstimatedHeight).coerceIn(0f, 1f)
    val thumbHeight = (viewportHeight * viewportRatio).coerceIn(minThumbHeightPx, viewportHeight)
    val maxThumbOffsetY = viewportHeight - thumbHeight
    val thumbOffsetY = scrollProgress * maxThumbOffsetY

    return ScrollBarMetrics(
        thumbHeight = thumbHeight,
        thumbOffsetY = thumbOffsetY,
        maxThumbOffsetY = maxThumbOffsetY,
    )
}

private fun Modifier.scrollbarDragInput(
    lazyListState: LazyListState,
    canScroll: Boolean,
    touchWidthPx: Float,
    estimatedItemHeightPx: Float,
    minThumbHeightPx: Float,
    coroutineScope: CoroutineScope,
    onDraggingChanged: (Boolean) -> Unit,
): Modifier = pointerInput(lazyListState, canScroll) {
    if (!canScroll) return@pointerInput

    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val touchX = down.position.x
        val touchY = down.position.y

        val currentTotalItems = lazyListState.layoutInfo.totalItemsCount
        val viewportHeight = size.height.toFloat()

        val metrics = calculateScrollBarMetrics(
            lazyListState = lazyListState,
            viewportHeight = viewportHeight,
            estimatedItemHeightPx = estimatedItemHeightPx,
            minThumbHeightPx = minThumbHeightPx,
        ) ?: return@awaitEachGesture

        val thumbTop = metrics.thumbOffsetY
        val thumbBottom = thumbTop + metrics.thumbHeight

        val isTouchOnThumb = touchX >= size.width - touchWidthPx && touchY in thumbTop..thumbBottom

        if (isTouchOnThumb) {
            down.consume()
            onDraggingChanged(true)

            fun scrollToTouch(y: Float) {
                if (currentTotalItems == 0 || viewportHeight <= 0f) return

                val targetThumbOffsetY = (y - metrics.thumbHeight / 2f).coerceIn(0f, metrics.maxThumbOffsetY)
                val targetFraction =
                    if (metrics.maxThumbOffsetY > 0f) targetThumbOffsetY / metrics.maxThumbOffsetY else 0f
                val targetIndex =
                    (targetFraction * (currentTotalItems - 1)).roundToInt().coerceIn(0, currentTotalItems - 1)

                coroutineScope.launch {
                    lazyListState.scrollToItem(targetIndex)
                }
            }

            scrollToTouch(touchY)

            drag(down.id) { change ->
                change.consume()
                scrollToTouch(change.position.y)
            }

            onDraggingChanged(false)
        }
    }
}

private fun Modifier.drawScrollBar(
    lazyListState: LazyListState,
    canScroll: Boolean,
    alpha: Float,
    color: Color,
    widthPx: Float,
    estimatedItemHeightPx: Float,
    minThumbHeightPx: Float,
): Modifier = drawWithContent {
    drawContent()

    if (canScroll && alpha > 0f) {
        val currentTotalItems = lazyListState.layoutInfo.totalItemsCount
        val currentVisibleItems = lazyListState.layoutInfo.visibleItemsInfo

        if (currentTotalItems > 0 && currentVisibleItems.isNotEmpty()) {
            val metrics = calculateScrollBarMetrics(
                lazyListState = lazyListState,
                viewportHeight = size.height,
                estimatedItemHeightPx = estimatedItemHeightPx,
                minThumbHeightPx = minThumbHeightPx,
            )

            if (metrics != null) {
                val x = size.width - widthPx
                drawRect(
                    color = color,
                    topLeft = Offset(x, metrics.thumbOffsetY),
                    size = Size(widthPx, metrics.thumbHeight),
                    alpha = alpha,
                )
            }
        }
    }
}
