package io.homeassistant.companion.android.frontend.gesture

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.View
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import io.homeassistant.companion.android.common.util.GestureDirection
import io.homeassistant.companion.android.util.OnSwipeListener
import io.homeassistant.companion.android.util.isEdgeReservedForSystemGestures
import kotlin.math.abs

/** Minimum swipe velocity (pixels/second) to trigger a gesture action. */
private const val MINIMUM_GESTURE_VELOCITY = 75f
private const val EDGE_WIDTH_DP = 24f
private const val MINIMUM_EDGE_SWIPE_DISTANCE_DP = 48f

/** Observes swipes without consuming touch events; the WebView still handles clicks and accessibility. */
@SuppressLint("ClickableViewAccessibility")
internal class FrontendSwipeListener(private val view: View, private val onGesture: (GestureDirection, Int) -> Unit) :
    OnSwipeListener(view.context) {

    private val density = view.resources.displayMetrics.density
    private var singlePointerGesture = false

    override fun onTouch(v: View?, event: MotionEvent?): Boolean {
        when (event?.actionMasked) {
            MotionEvent.ACTION_DOWN -> singlePointerGesture = true
            MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_CANCEL -> singlePointerGesture = false
        }
        return super.onTouch(v, event)
    }

    override fun onSwipe(
        e1: MotionEvent,
        e2: MotionEvent,
        velocity: Float,
        direction: GestureDirection,
        pointerCount: Int,
    ): Boolean {
        val isEdgeSwipe = singlePointerGesture && isInwardEdgeSwipe(e1, e2, direction)
        if (
            velocity >= MINIMUM_GESTURE_VELOCITY &&
            (pointerCount > 1 || isEdgeSwipe)
        ) {
            onGesture(direction, pointerCount)
        }
        return false
    }

    override fun onMotionEventHandled(v: View?, event: MotionEvent?): Boolean = false

    private fun isInwardEdgeSwipe(start: MotionEvent, end: MotionEvent, direction: GestureDirection): Boolean {
        val distanceX = end.x - start.x
        val distanceY = end.y - start.y
        val startsInsideView = view.width > 0 && start.x in 0f..view.width.toFloat()
        if (
            !startsInsideView ||
            abs(distanceX) < MINIMUM_EDGE_SWIPE_DISTANCE_DP * density ||
            abs(distanceX) <= abs(distanceY)
        ) {
            return false
        }

        val movesInward = when (direction) {
            GestureDirection.RIGHT -> distanceX > 0
            GestureDirection.LEFT -> distanceX < 0
            GestureDirection.UP, GestureDirection.DOWN -> false
        }
        return movesInward && startsAtAvailableEdge(start.x, direction)
    }

    private fun startsAtAvailableEdge(startX: Float, direction: GestureDirection): Boolean {
        val edgeWidth = EDGE_WIDTH_DP * density
        val windowInsets = ViewCompat.getRootWindowInsets(view)
        val systemGestures = windowInsets?.getInsets(WindowInsetsCompat.Type.systemGestures()) ?: Insets.NONE
        val tappableElements = windowInsets?.getInsets(WindowInsetsCompat.Type.tappableElement()) ?: Insets.NONE
        // Account for insets already applied by the parent when locating the window's touchable edges.
        val root = view.rootView
        val rootLocation = IntArray(2).also(root::getLocationInWindow)
        val viewLocation = IntArray(2).also(view::getLocationInWindow)
        val offsetX = viewLocation[0] - rootLocation[0]
        val leftEdge = (tappableElements.left - offsetX).coerceAtLeast(0).toFloat()
        val rightEdge = (root.width - tappableElements.right - offsetX).coerceAtMost(view.width).toFloat()
        if (startX !in leftEdge..rightEdge) return false

        return when (direction) {
            GestureDirection.RIGHT ->
                !isEdgeReservedForSystemGestures(
                    systemGestureInset = systemGestures.left,
                    tappableElementInset = tappableElements.left,
                ) &&
                    startX <= leftEdge + edgeWidth
            GestureDirection.LEFT ->
                !isEdgeReservedForSystemGestures(
                    systemGestureInset = systemGestures.right,
                    tappableElementInset = tappableElements.right,
                ) &&
                    startX >= rightEdge - edgeWidth
            GestureDirection.UP, GestureDirection.DOWN -> false
        }
    }
}
