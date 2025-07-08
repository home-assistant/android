package io.homeassistant.companion.android.common.util

import kotlin.math.abs

enum class GestureAction {
    NONE,
    QUICKBAR_DEFAULT,
    SERVER_LIST,
    SERVER_NEXT,
    SERVER_PREVIOUS,
    ;
}

enum class GestureDirection {
    /** A gesture from bottom to top */
    UP,
    /** A gesture from top to bottom */
    DOWN,
    /** A gesture from right to left */
    LEFT,
    /** A gesture from left to right */
    RIGHT,
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