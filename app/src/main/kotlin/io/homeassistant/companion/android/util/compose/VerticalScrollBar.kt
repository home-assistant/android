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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Adds an interactive vertical scrollbar to a composable with a [LazyListState].
 *
 * @param lazyListState State of the scrollable list.
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
    val rightPaddingPx = 0f
    val estimatedItemHeightPx = with(density) { estimatedItemHeight.toPx() }

    var isDragging by remember { mutableStateOf(false) }
    val isScrolling = lazyListState.isScrollInProgress

    val alpha = remember { Animatable(0f) }

    val canScroll by remember {
        derivedStateOf {
            val layoutInfo = lazyListState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val visibleItems = layoutInfo.visibleItemsInfo
            if (totalItems == 0 || visibleItems.isEmpty()) {
                false
            } else {
                visibleItems.size < totalItems ||
                    lazyListState.firstVisibleItemScrollOffset > 0 ||
                    visibleItems.last().size > layoutInfo.viewportSize.height - visibleItems.last().offset
            }
        }
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
        .pointerInput(lazyListState, canScroll) {
            if (!canScroll) return@pointerInput

            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val touchX = down.position.x
                val touchY = down.position.y

                val currentLayoutInfo = lazyListState.layoutInfo
                val currentTotalItems = currentLayoutInfo.totalItemsCount
                if (currentTotalItems == 0) return@awaitEachGesture

                val viewportHeight = size.height.toFloat()
                val totalEstimatedHeight = currentTotalItems * estimatedItemHeightPx
                val maxScrolledOffset = (totalEstimatedHeight - viewportHeight).coerceAtLeast(1f)
                val scrolledOffset =
                    lazyListState.firstVisibleItemIndex * estimatedItemHeightPx +
                        lazyListState.firstVisibleItemScrollOffset
                val scrollProgress = (scrolledOffset / maxScrolledOffset).coerceIn(0f, 1f)

                val viewportRatio = (viewportHeight / totalEstimatedHeight).coerceIn(0f, 1f)
                val thumbHeight = (viewportHeight * viewportRatio).coerceIn(minThumbHeightPx, viewportHeight)
                val maxThumbOffsetY = viewportHeight - thumbHeight
                val thumbTop = scrollProgress * maxThumbOffsetY
                val thumbBottom = thumbTop + thumbHeight

                val isTouchOnThumb = touchX >= size.width - touchWidthPx && touchY in thumbTop..thumbBottom

                if (isTouchOnThumb) {
                    down.consume()
                    isDragging = true

                    fun scrollToTouch(y: Float) {
                        if (currentTotalItems == 0) return
                        if (viewportHeight <= 0f) return

                        val targetThumbOffsetY = (y - thumbHeight / 2f).coerceIn(0f, maxThumbOffsetY)
                        val targetFraction = if (maxThumbOffsetY > 0f) targetThumbOffsetY / maxThumbOffsetY else 0f
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

                    isDragging = false
                }
            }
        }
        .drawWithContent {
            drawContent()

            if (canScroll && alpha.value > 0f) {
                val currentLayoutInfo = lazyListState.layoutInfo
                val currentTotalItems = currentLayoutInfo.totalItemsCount
                val currentVisibleItems = currentLayoutInfo.visibleItemsInfo

                if (currentTotalItems > 0 && currentVisibleItems.isNotEmpty()) {
                    val viewportHeight = size.height
                    val firstVisibleIndex = lazyListState.firstVisibleItemIndex
                    val firstVisibleOffset = lazyListState.firstVisibleItemScrollOffset

                    val totalEstimatedHeight = currentTotalItems * estimatedItemHeightPx
                    val maxScrolledOffset = (totalEstimatedHeight - viewportHeight).coerceAtLeast(1f)
                    val scrolledOffset = firstVisibleIndex * estimatedItemHeightPx + firstVisibleOffset
                    val scrollProgress = (scrolledOffset / maxScrolledOffset).coerceIn(0f, 1f)

                    val viewportRatio = (viewportHeight / totalEstimatedHeight).coerceIn(0f, 1f)
                    val thumbHeight = (viewportHeight * viewportRatio).coerceIn(minThumbHeightPx, viewportHeight)
                    val maxThumbOffsetY = viewportHeight - thumbHeight
                    val thumbOffsetY = scrollProgress * maxThumbOffsetY

                    val x = size.width - rightPaddingPx - widthPx

                    drawRect(
                        color = color,
                        topLeft = Offset(x, thumbOffsetY),
                        size = Size(widthPx, thumbHeight),
                        alpha = alpha.value,
                    )
                }
            }
        }
}
