package io.homeassistant.companion.android.frontend.gesture

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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, qualifiers = "mdpi")
class FrontendSwipeListenerTest {

    private lateinit var view: View
    private lateinit var listener: FrontendSwipeListener
    private val gestures = mutableListOf<DetectedGesture>()
    private var density = 1f

    @Before
    fun setup() {
        view = View(ApplicationProvider.getApplicationContext())
        density = view.resources.displayMetrics.density
        view.layout(0, 0, (1000 * density).toInt(), (800 * density).toInt())
        listener = FrontendSwipeListener(view) { direction, pointerCount ->
            gestures.add(DetectedGesture(direction, pointerCount))
        }
    }

    @Test
    fun `Given inward swipes from both edges when released then corresponding actions are reported`() {
        swipe(startX = 8f, endX = 200f)
        swipe(startX = 992f, endX = 800f)

        assertEquals(
            listOf(DetectedGesture(GestureDirection.RIGHT, 1), DetectedGesture(GestureDirection.LEFT, 1)),
            gestures,
        )
    }

    @Test
    fun `Given interior swipes when released then no action is reported`() {
        swipe(startX = 400f, endX = 600f)
        swipe(startX = 600f, endX = 400f)
        swipe(startX = 25f, endX = 200f)
        swipe(startX = 975f, endX = 800f)

        assertTrue(gestures.isEmpty())
    }

    @Test
    fun `Given outward swipes from either edge when released then no action is reported`() {
        swipe(startX = 24f, endX = -200f)
        swipe(startX = 976f, endX = 1200f)

        assertTrue(gestures.isEmpty())
    }

    @Test
    fun `Given vertical or diagonal swipes at an edge when released then no action is reported`() {
        swipe(startX = 8f, endX = 8f, endY = 600f)
        swipe(startX = 8f, endX = 120f, endY = 600f)
        swipe(startX = 992f, endX = 880f, endY = 200f)

        assertTrue(gestures.isEmpty())
    }

    @Test
    fun `Given taps short swipes or slow drags at an edge when released then no action is reported`() {
        swipe(startX = 8f, endX = 8f)
        swipe(startX = 8f, endX = 40f)
        swipe(startX = 8f, endX = 200f, durationMillis = 10_000)

        assertTrue(gestures.isEmpty())
    }

    @Test
    fun `Given a cancelled edge swipe when a new interior swipe occurs then no action is reported`() {
        touch(MotionEvent.ACTION_DOWN, x = 8f, timeMillis = 0)
        touch(MotionEvent.ACTION_MOVE, x = 200f, timeMillis = 60)
        touch(MotionEvent.ACTION_CANCEL, x = 250f, timeMillis = 100)
        swipe(startX = 400f, endX = 600f)

        assertTrue(gestures.isEmpty())
    }

    @Test
    fun `Given multiple fingers at an edge when one remains after the delay then no edge action is reported`() {
        touch(MotionEvent.ACTION_DOWN, x = 8f, timeMillis = 0)
        touch(
            MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
            x = 8f,
            timeMillis = 10,
            pointerCount = 2,
        )
        touch(
            MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
            x = 8f,
            timeMillis = 20,
            pointerCount = 2,
        )
        shadowOf(Looper.getMainLooper()).idleFor(501, TimeUnit.MILLISECONDS)
        touch(MotionEvent.ACTION_MOVE, x = 100f, timeMillis = 600)
        touch(MotionEvent.ACTION_MOVE, x = 200f, timeMillis = 630)
        touch(MotionEvent.ACTION_UP, x = 300f, timeMillis = 660)

        assertTrue(gestures.isEmpty())
    }

    @Test
    fun `Given an edge swipe that reverses direction when released then no action is reported`() {
        touch(MotionEvent.ACTION_DOWN, x = 8f, timeMillis = 0)
        touch(MotionEvent.ACTION_MOVE, x = 300f, timeMillis = 200)
        touch(MotionEvent.ACTION_MOVE, x = 200f, timeMillis = 230)
        touch(MotionEvent.ACTION_MOVE, x = 140f, timeMillis = 260)
        touch(MotionEvent.ACTION_UP, x = 80f, timeMillis = 290)

        assertTrue(gestures.isEmpty())
    }

    @Test
    @Config(qualifiers = "xhdpi")
    fun `Given a high density screen when swiping then distances are measured in dp`() {
        swipe(startX = 25f, endX = 200f)
        swipe(startX = 8f, endX = 40f)
        assertTrue(gestures.isEmpty())

        swipe(startX = 8f, endX = 200f)

        assertEquals(listOf(DetectedGesture(GestureDirection.RIGHT, 1)), gestures)
    }

    @Test
    fun `Given an edge swipe in progress when moving then no action is reported before release`() {
        touch(MotionEvent.ACTION_DOWN, x = 8f, timeMillis = 0)
        touch(MotionEvent.ACTION_MOVE, x = 100f, timeMillis = 30)
        touch(MotionEvent.ACTION_MOVE, x = 200f, timeMillis = 60)
        assertTrue(gestures.isEmpty())

        touch(MotionEvent.ACTION_UP, x = 300f, timeMillis = 90)

        assertEquals(listOf(DetectedGesture(GestureDirection.RIGHT, 1)), gestures)
    }

    private fun swipe(startX: Float, endX: Float, endY: Float = 400f, durationMillis: Long = 120) {
        touch(MotionEvent.ACTION_DOWN, x = startX, timeMillis = 0)
        for (step in 1..4) {
            val fraction = step / 4f
            touch(
                action = if (step == 4) MotionEvent.ACTION_UP else MotionEvent.ACTION_MOVE,
                x = startX + (endX - startX) * fraction,
                y = 400f + (endY - 400f) * fraction,
                timeMillis = durationMillis * step / 4,
            )
        }
    }

    private fun touch(action: Int, x: Float, timeMillis: Long, y: Float = 400f, pointerCount: Int = 1) {
        val event = MotionEvent.obtain(
            0,
            timeMillis,
            action,
            pointerCount,
            Array(pointerCount) { index ->
                MotionEvent.PointerProperties().apply {
                    id = index
                    toolType = MotionEvent.TOOL_TYPE_FINGER
                }
            },
            Array(pointerCount) {
                MotionEvent.PointerCoords().apply {
                    this.x = x * density
                    this.y = y * density
                    pressure = 1f
                    size = 1f
                }
            },
            0,
            0,
            1f,
            1f,
            0,
            0,
            InputDevice.SOURCE_TOUCHSCREEN,
            0,
        )
        try {
            assertFalse(listener.onTouch(view, event))
        } finally {
            event.recycle()
        }
    }

    private data class DetectedGesture(val direction: GestureDirection, val pointerCount: Int)
}
