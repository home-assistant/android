package io.homeassistant.companion.android.util

import android.content.Context
import android.os.Looper
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import androidx.test.core.app.ApplicationProvider
import dagger.hilt.android.testing.HiltTestApplication
import io.homeassistant.companion.android.common.util.GestureDirection
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

private const val DOWN_TIME_MILLIS = 1_000L

@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
@LooperMode(LooperMode.Mode.PAUSED)
class OnSwipeListenerTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val view = View(context)
    private val swipes = mutableListOf<Swipe>()
    private val listener = object : OnSwipeListener(context) {
        override fun onSwipe(
            e1: MotionEvent,
            e2: MotionEvent,
            velocity: Float,
            direction: GestureDirection,
            pointerCount: Int,
        ): Boolean {
            swipes += Swipe(direction, pointerCount, velocity)
            return false
        }

        override fun onMotionEventHandled(v: View?, event: MotionEvent?): Boolean = false
    }

    @Test
    fun `Given nonzero pointer id when swiping then report its direction and count`() {
        swipeTogether(listOf(7), deltaX = 240f, deltaY = 0f)

        assertSwipe(GestureDirection.RIGHT, pointerCount = 1)
    }

    @Test
    fun `Given two pointers when swiping together then retain two pointer gesture`() {
        swipeTogether(listOf(0, 1), deltaX = -240f, deltaY = 0f)

        assertSwipe(GestureDirection.LEFT, pointerCount = 2)
    }

    @Test
    fun `Given three pointers when swiping together then retain three pointer gesture`() {
        swipeTogether(listOf(0, 1, 2), deltaX = 0f, deltaY = -240f)

        assertSwipe(GestureDirection.UP, pointerCount = 3)
    }

    @Test
    fun `Given recycled pointer up event when delay expires then track captured remaining pointer`() {
        send(MotionEvent.ACTION_DOWN, 0, point(7, 20f))
        send(pointerAction(MotionEvent.ACTION_POINTER_DOWN, 1), 8, point(7, 20f), point(11, 20f))
        send(MotionEvent.ACTION_MOVE, 16, point(7, 60f), point(11, 60f))
        send(pointerAction(MotionEvent.ACTION_POINTER_UP, 0), 32, point(7, 100f), point(11, 100f))

        // Reuse the recycled event's pool entry before the pending pointer-count update runs.
        val pooledEvent = event(MotionEvent.ACTION_DOWN, 40, listOf(point(19, 0f)))
        try {
            shadowOf(Looper.getMainLooper()).idleFor(501, TimeUnit.MILLISECONDS)
            send(MotionEvent.ACTION_MOVE, 560, point(11, 100f))
            send(MotionEvent.ACTION_MOVE, 576, point(11, 180f))
            send(MotionEvent.ACTION_UP, 592, point(11, 260f))
        } finally {
            pooledEvent.recycle()
        }

        assertSwipe(GestureDirection.RIGHT, pointerCount = 1)
    }

    @Test
    fun `Given replacement pointer before delay when swiping then use current pointer ids`() {
        send(MotionEvent.ACTION_DOWN, 0, point(7, 20f))
        send(pointerAction(MotionEvent.ACTION_POINTER_DOWN, 1), 8, point(7, 20f), point(11, 20f))
        send(MotionEvent.ACTION_MOVE, 16, point(7, 60f), point(11, 60f))
        send(pointerAction(MotionEvent.ACTION_POINTER_UP, 0), 24, point(7, 100f), point(11, 100f))
        send(pointerAction(MotionEvent.ACTION_POINTER_DOWN, 1), 32, point(11, 140f), point(19, 140f))
        send(MotionEvent.ACTION_MOVE, 48, point(11, 200f), point(19, 200f))
        send(MotionEvent.ACTION_MOVE, 64, point(11, 260f), point(19, 260f))
        send(pointerAction(MotionEvent.ACTION_POINTER_UP, 1), 72, point(11, 300f), point(19, 300f))
        send(MotionEvent.ACTION_UP, 80, point(11, 340f))

        assertSwipe(GestureDirection.RIGHT, pointerCount = 2)
    }

    @Test
    fun `Given cancelled stream when starting next swipe then report only new gesture`() {
        send(MotionEvent.ACTION_DOWN, 0, point(7, 20f))
        send(MotionEvent.ACTION_MOVE, 16, point(7, 100f))
        send(MotionEvent.ACTION_CANCEL, 32, point(7, 180f))

        assertTrue(swipes.isEmpty())
        swipeTogether(listOf(11), deltaX = -240f, deltaY = 0f)
        assertSwipe(GestureDirection.LEFT, pointerCount = 1)
    }

    @Test
    fun `Given unfinished stream when new down arrives then old timer cannot replace current pointer`() {
        send(MotionEvent.ACTION_DOWN, 0, point(7, 20f))
        send(pointerAction(MotionEvent.ACTION_POINTER_DOWN, 1), 8, point(7, 20f), point(11, 20f))
        send(MotionEvent.ACTION_MOVE, 16, point(7, 60f), point(11, 60f))
        send(pointerAction(MotionEvent.ACTION_POINTER_UP, 0), 32, point(7, 100f), point(11, 100f))
        send(MotionEvent.ACTION_DOWN, 40, point(19, 20f))
        shadowOf(Looper.getMainLooper()).idleFor(501, TimeUnit.MILLISECONDS)
        send(MotionEvent.ACTION_MOVE, 560, point(19, 20f))
        send(MotionEvent.ACTION_MOVE, 576, point(19, 100f))
        send(MotionEvent.ACTION_UP, 592, point(19, 180f))

        assertSwipe(GestureDirection.RIGHT, pointerCount = 1)
    }

    private fun swipeTogether(ids: List<Int>, deltaX: Float, deltaY: Float) {
        fun points(fraction: Float, activeIds: List<Int> = ids) = activeIds.map { id ->
            TouchPoint(id, x = 300f + deltaX * fraction, y = 400f + deltaY * fraction)
        }

        send(MotionEvent.ACTION_DOWN, 0, *points(0f, ids.take(1)).toTypedArray())
        for (index in 1 until ids.size) {
            send(
                pointerAction(MotionEvent.ACTION_POINTER_DOWN, index),
                index * 4L,
                *points(0f, ids.take(index + 1)).toTypedArray(),
            )
        }
        send(MotionEvent.ACTION_MOVE, 16, *points(0.25f).toTypedArray())
        send(MotionEvent.ACTION_MOVE, 32, *points(0.5f).toTypedArray())
        send(MotionEvent.ACTION_MOVE, 48, *points(0.75f).toTypedArray())
        for (index in ids.lastIndex downTo 1) {
            send(
                pointerAction(MotionEvent.ACTION_POINTER_UP, index),
                56L + (ids.lastIndex - index) * 4L,
                *points(0.9f, ids.take(index + 1)).toTypedArray(),
            )
        }
        send(MotionEvent.ACTION_UP, 64, *points(1f, ids.take(1)).toTypedArray())
    }

    private fun assertSwipe(direction: GestureDirection, pointerCount: Int) {
        assertEquals(1, swipes.size)
        val swipe = swipes.single()
        assertEquals(direction, swipe.direction)
        assertEquals(pointerCount, swipe.pointerCount)
        assertTrue(swipe.velocity > 0f)
    }

    private fun point(id: Int, x: Float) = TouchPoint(id, x, y = 200f)

    private fun pointerAction(action: Int, index: Int) = action or (index shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)

    private fun send(action: Int, timeMillis: Long, vararg points: TouchPoint) {
        val event = event(action, timeMillis, points.toList())
        try {
            assertFalse(listener.onTouch(view, event))
        } finally {
            event.recycle()
        }
    }

    private fun event(action: Int, timeMillis: Long, points: List<TouchPoint>): MotionEvent = MotionEvent.obtain(
        DOWN_TIME_MILLIS,
        DOWN_TIME_MILLIS + timeMillis,
        action,
        points.size,
        points.map { point ->
            MotionEvent.PointerProperties().apply {
                id = point.id
                toolType = MotionEvent.TOOL_TYPE_FINGER
            }
        }.toTypedArray(),
        points.map { point ->
            MotionEvent.PointerCoords().apply {
                x = point.x
                y = point.y
                pressure = 1f
                size = 1f
            }
        }.toTypedArray(),
        0,
        0,
        1f,
        1f,
        0,
        0,
        InputDevice.SOURCE_TOUCHSCREEN,
        0,
    )

    private data class TouchPoint(val id: Int, val x: Float, val y: Float)

    private data class Swipe(val direction: GestureDirection, val pointerCount: Int, val velocity: Float)
}
