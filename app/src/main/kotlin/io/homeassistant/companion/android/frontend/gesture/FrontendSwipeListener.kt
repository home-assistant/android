package io.homeassistant.companion.android.frontend.gesture

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import io.homeassistant.companion.android.common.util.GestureDirection
import io.homeassistant.companion.android.util.OnSwipeListener
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

        val edgeWidth = EDGE_WIDTH_DP * density
        return !isEdgeReserved(direction) &&
            when (direction) {
                GestureDirection.RIGHT -> start.x <= edgeWidth && distanceX > 0
                GestureDirection.LEFT ->
                    start.x >= view.width - edgeWidth && distanceX < 0
                GestureDirection.UP, GestureDirection.DOWN -> false
            }
    }

    private fun isEdgeReserved(direction: GestureDirection): Boolean {
        val windowInsets = ViewCompat.getRootWindowInsets(view) ?: return false
        val systemGestures = windowInsets.getInsets(WindowInsetsCompat.Type.systemGestures())
        val tappableElements = windowInsets.getInsets(WindowInsetsCompat.Type.tappableElement())
        return when (direction) {
            GestureDirection.RIGHT -> systemGestures.left > tappableElements.left
            GestureDirection.LEFT -> systemGestures.right > tappableElements.right
            GestureDirection.UP, GestureDirection.DOWN -> false
        }
    }
}
