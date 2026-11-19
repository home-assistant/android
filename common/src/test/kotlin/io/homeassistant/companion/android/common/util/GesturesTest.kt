package io.homeassistant.companion.android.common.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class GesturesTest {

    @ParameterizedTest
    @CsvSource(
        "LEFT,1,SWIPE_LEFT_ONE_EDGE",
        "RIGHT,1,SWIPE_RIGHT_ONE_EDGE",
        "LEFT,2,SWIPE_LEFT_TWO",
        "RIGHT,2,SWIPE_RIGHT_TWO",
        "UP,2,SWIPE_UP_TWO",
        "DOWN,2,SWIPE_DOWN_TWO",
        "LEFT,3,SWIPE_LEFT_THREE",
        "RIGHT,3,SWIPE_RIGHT_THREE",
        "UP,3,SWIPE_UP_THREE",
        "DOWN,3,SWIPE_DOWN_THREE",
    )
    fun `Given supported direction and pointer count when mapping swipe then matching gesture is returned`(
        direction: GestureDirection,
        pointers: Int,
        expected: HAGesture,
    ) {
        assertEquals(expected, HAGesture.fromSwipeListener(direction = direction, pointers = pointers))
    }

    @ParameterizedTest
    @CsvSource(
        "UP,1",
        "DOWN,1",
        "LEFT,0",
        "RIGHT,4",
    )
    fun `Given unsupported direction or pointer count when mapping swipe then no gesture is returned`(
        direction: GestureDirection,
        pointers: Int,
    ) {
        assertNull(HAGesture.fromSwipeListener(direction = direction, pointers = pointers))
    }
}
