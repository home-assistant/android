package io.homeassistant.companion.android.util

import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import kotlin.math.abs

// Adapted from the system GestureDetector/GestureListener and https://stackoverflow.com/a/26387629
// We need to keep track of (pointer) down, move, (pointer) up and cancel events to be able to detect flings
// (or swipes) and send the direction + number of pointers for that fling to the app
abstract class OnSwipeListener : View.OnTouchListener {
    var handler = Handler(Looper.getMainLooper())

    var velocityTracker: VelocityTracker? = null
    var downEvent: MotionEvent? = null
    var numberOfPointers = 0

    var minimumFlingVelocity = ViewConfiguration.getMinimumFlingVelocity()
    var maximumFlingVelocity = ViewConfiguration.getMaximumFlingVelocity()

    override fun onTouch(v: View?, event: MotionEvent?): Boolean {
        event?.let { motionEvent ->
            if (velocityTracker == null) velocityTracker = VelocityTracker.obtain()
            velocityTracker?.addMovement(event)

            when (motionEvent.action and MotionEvent.ACTION_MASK) {
                MotionEvent.ACTION_DOWN -> {
                    downEvent?.recycle()
                    downEvent = MotionEvent.obtain(motionEvent)
                    numberOfPointers = 1
                }
                MotionEvent.ACTION_POINTER_DOWN -> {
                    if (motionEvent.pointerCount > numberOfPointers) {
                        handler.removeCallbacksAndMessages(null)
                        numberOfPointers = motionEvent.pointerCount
                    }
                }
                MotionEvent.ACTION_POINTER_UP -> {
                    if ((motionEvent.pointerCount - 1) < numberOfPointers) {
                        // Delay lowering the pointer count, to make sure that if all pointers are removed at once
                        // we are still able to give the 'full' count but also don't give a count too high if the user
                        // accidentally had another pointer on screen at the start
                        handler.removeCallbacksAndMessages(null)
                        handler.postDelayed({
                            numberOfPointers = motionEvent.pointerCount - 1
                        }, 500)
                    }

                    // From the GestureDetector: check the dot product of current velocities.
                    // If the pointer that left was opposing another velocity vector, clear.
                    calculateVelocityForView(v)
                    velocityTracker?.computeCurrentVelocity(1000, maximumFlingVelocity.toFloat())
                    val upIndex = motionEvent.actionIndex
                    val id1 = motionEvent.getPointerId(upIndex)
                    val x1 = velocityTracker?.getXVelocity(id1) ?: 0f
                    val y1 = velocityTracker?.getYVelocity(id1) ?: 0f
                    for (i in 0 until motionEvent.pointerCount) {
                        if (i == upIndex) continue
                        val id2 = motionEvent.getPointerId(i)
                        val x = x1 * (velocityTracker?.getXVelocity(id2) ?: 0f)
                        val y = y1 * (velocityTracker?.getYVelocity(id2) ?: 0f)
                        val dot = x + y
                        if (dot < 0) {
                            velocityTracker?.clear()
                            if ((motionEvent.pointerCount - 1) < numberOfPointers) {
                                handler.removeCallbacksAndMessages(null)
                                numberOfPointers = motionEvent.pointerCount - 1
                            }
                            break
                        }
                    }
                }
                MotionEvent.ACTION_UP -> {
                    var handled: Boolean? = null
                    calculateVelocityForView(v)
                    velocityTracker?.computeCurrentVelocity(1000, maximumFlingVelocity.toFloat())
                    var velocityX = 0f
                    var velocityY = 0f
                    velocityTracker?.let {
                        // Take average of velocities of all pointers to prevent individual outliers
                        for (i in 0 until numberOfPointers) {
                            velocityX += it.getXVelocity(i)
                            velocityY += it.getYVelocity(i)
                        }
                        velocityX /= numberOfPointers
                        velocityY /= numberOfPointers
                    }
                    if (
                        abs(velocityX) > minimumFlingVelocity ||
                        abs(velocityY) > minimumFlingVelocity
                    ) {
                        // ≈ onFling in GestureDetector.OnGestureListener
                        // Calculate the direction based on which velocity is highest
                        val direction = SwipeDirection.fromVelocity(velocityX, velocityY)
                        handled = onSwipe(
                            downEvent!!,
                            motionEvent,
                            abs(if (direction == SwipeDirection.UP || direction == SwipeDirection.DOWN) velocityY else velocityX),
                            direction,
                            numberOfPointers
                        )
                    }
                    cleanup()
                    if (handled != null) return handled
                }
                MotionEvent.ACTION_CANCEL -> cleanup()
            }
        }
        return onMotionEventHandled(v, event)
    }

    private fun calculateVelocityForView(view: View?) {
        if (view?.context != null) {
            minimumFlingVelocity = ViewConfiguration.get(view.context).scaledMinimumFlingVelocity
            maximumFlingVelocity = ViewConfiguration.get(view.context).scaledMaximumFlingVelocity
        }
    }

    private fun cleanup() {
        handler.removeCallbacksAndMessages(null)

        velocityTracker?.recycle()
        velocityTracker = null
        downEvent?.recycle()
        downEvent = null
        numberOfPointers = 0
    }

    abstract fun onSwipe(
        e1: MotionEvent,
        e2: MotionEvent,
        velocity: Float,
        direction: SwipeDirection,
        pointerCount: Int
    ): Boolean

    abstract fun onMotionEventHandled(
        v: View?,
        event: MotionEvent?
    ): Boolean

    enum class SwipeDirection {
        UP, DOWN, LEFT, RIGHT;

        companion object {
            fun fromVelocity(velocityX: Float, velocityY: Float): SwipeDirection {
                return if (abs(velocityX) > abs(velocityY)) {
                    if (velocityX > 0) RIGHT
                    else LEFT
                } else {
                    if (velocityY > 0) DOWN
                    else UP
                }
            }
        }
    }
}
