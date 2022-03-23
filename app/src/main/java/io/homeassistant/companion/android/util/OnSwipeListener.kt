package io.homeassistant.companion.android.util

import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import kotlin.math.abs
import kotlin.math.atan2

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
                        }, 600)
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
                            break
                        }
                    }
                }
                MotionEvent.ACTION_UP -> {
                    var handled: Boolean? = null
                    calculateVelocityForView(v)
                    velocityTracker?.computeCurrentVelocity(1000, maximumFlingVelocity.toFloat())
                    val velocityX = velocityTracker?.getXVelocity(motionEvent.getPointerId(0))
                    val velocityY = velocityTracker?.getYVelocity(motionEvent.getPointerId(0))
                    if (
                        velocityX != null && velocityY != null &&
                        ((abs(velocityY) > minimumFlingVelocity) || (abs(velocityX) > minimumFlingVelocity))
                    ) {
                        // = onFling in GestureDetector.OnGestureListener
                        // Calculate the position of motionEvent relative to downEvent to find the fling direction
                        val direction = getDirection(downEvent!!.x, downEvent!!.y, motionEvent.x, motionEvent.y)
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

    /**
     * Given two points in the plane p1=(x1, x2) and p2=(y1, y1), this method
     * returns the direction that an arrow pointing from p1 to p2 would have.
     * @param x1 the x position of the first point
     * @param y1 the y position of the first point
     * @param x2 the x position of the second point
     * @param y2 the y position of the second point
     * @return the direction
     */
    private fun getDirection(x1: Float, y1: Float, x2: Float, y2: Float): SwipeDirection {
        val angle = getAngle(x1, y1, x2, y2)
        return SwipeDirection.fromAngle(angle)
    }

    /**
     * Finds the angle between two points in the plane (x1,y1) and (x2, y2)
     * The angle is measured with 0/360 being the X-axis to the right, angles
     * increase counter clockwise.
     *
     * @param x1 the x position of the first point
     * @param y1 the y position of the first point
     * @param x2 the x position of the second point
     * @param y2 the y position of the second point
     * @return the angle between two points
     */
    private fun getAngle(x1: Float, y1: Float, x2: Float, y2: Float): Double {
        val rad = atan2((y1 - y2).toDouble(), (x2 - x1).toDouble()) + Math.PI
        return (rad * 180 / Math.PI + 180) % 360
    }

    enum class SwipeDirection {
        UP, DOWN, LEFT, RIGHT;

        companion object {
            /**
             * Returns a direction given an angle.
             * Directions are defined as follows:
             *
             * Up: [45, 135]
             * Right: [0,45] and [315, 360]
             * Down: [225, 315]
             * Left: [135, 225]
             *
             * @param angle an angle from 0 to 360 - e
             * @return the direction of an angle
             */
            fun fromAngle(angle: Double): SwipeDirection {
                return if (angle in (45f..135f)) {
                    UP
                } else if (angle in (0f..45f) || angle in (315f..360f)) {
                    RIGHT
                } else if (angle in (225f..315f)) {
                    DOWN
                } else {
                    LEFT
                }
            }
        }
    }
}
