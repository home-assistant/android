package io.homeassistant.companion.android.common.util

import io.homeassistant.companion.android.common.R
import kotlin.math.abs

enum class GestureAction(val description: Int) {
    NONE(R.string.none),
    QUICKBAR_DEFAULT(R.string.gestures_action_quickbar_default),
    SERVER_LIST(R.string.gestures_action_server_list),
    SERVER_NEXT(R.string.gestures_action_server_next),
    SERVER_PREVIOUS(R.string.gestures_action_server_previous),
}

enum class GestureDirection(val description: Int) {
    /** A gesture from bottom to top */
    UP(R.string.gestures_direction_up),

    /** A gesture from top to bottom */
    DOWN(R.string.gestures_direction_down),

    /** A gesture from right to left */
    LEFT(R.string.gestures_direction_left),

    /** A gesture from left to right */
    RIGHT(R.string.gestures_direction_right),
    ;

    companion object {
        fun fromVelocity(velocityX: Float, velocityY: Float): GestureDirection {
            return if (abs(velocityX) > abs(velocityY)) {
                if (velocityX > 0) {
                    RIGHT
                } else {
                    LEFT
                }
            } else {
                if (velocityY > 0) {
                    DOWN
                } else {
                    UP
                }
            }
        }
    }
}

/**
 * Enum with all supported gestures by the app. Currently, a gesture
 * is always a combination of swipe + a direction and pointer count.
 */
enum class HAGesture(
    val direction: GestureDirection,
    val pointers: Int,
) {
    SWIPE_LEFT_TWO(GestureDirection.LEFT, 2),
    SWIPE_LEFT_THREE(GestureDirection.LEFT, 3),
    SWIPE_RIGHT_TWO(GestureDirection.RIGHT, 2),
    SWIPE_RIGHT_THREE(GestureDirection.RIGHT, 3),
    SWIPE_UP_TWO(GestureDirection.UP, 2),
    SWIPE_UP_THREE(GestureDirection.UP, 3),
    SWIPE_DOWN_TWO(GestureDirection.DOWN, 2),
    SWIPE_DOWN_THREE(GestureDirection.DOWN, 3),
    ;

    companion object {
        fun fromSwipeListener(direction: GestureDirection, pointers: Int): HAGesture? =
            entries.firstOrNull { it.direction == direction && it.pointers == pointers }
    }
}
