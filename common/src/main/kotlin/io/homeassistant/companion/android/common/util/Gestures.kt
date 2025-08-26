package io.homeassistant.companion.android.common.util

import androidx.annotation.Keep
import androidx.annotation.StringRes
import io.homeassistant.companion.android.common.R
import kotlin.math.abs

enum class GestureAction(@StringRes val description: Int, val category: GestureActionCategory) {
    NONE(R.string.none, GestureActionCategory.NONE),
    QUICKBAR_DEFAULT(R.string.gestures_action_quickbar_default, GestureActionCategory.FRONTEND),
    QUICKBAR_DEVICES(R.string.gestures_action_quickbar_devices, GestureActionCategory.FRONTEND),
    QUICKBAR_COMMANDS(R.string.gestures_action_quickbar_commands, GestureActionCategory.FRONTEND),
    SHOW_SIDEBAR(R.string.gestures_action_show_sidebar, GestureActionCategory.FRONTEND),
    OPEN_ASSIST(R.string.gestures_action_open_assist, GestureActionCategory.FRONTEND),
    NAVIGATE_FORWARD(R.string.gestures_action_navigate_forward, GestureActionCategory.NAVIGATION),
    NAVIGATE_DASHBOARD(R.string.gestures_action_navigate_dashboard, GestureActionCategory.NAVIGATION),
    NAVIGATE_RELOAD(R.string.gestures_action_navigate_reload, GestureActionCategory.NAVIGATION),
    SERVER_LIST(R.string.gestures_action_server_list, GestureActionCategory.SERVERS),
    SERVER_NEXT(R.string.gestures_action_server_next, GestureActionCategory.SERVERS),
    SERVER_PREVIOUS(R.string.gestures_action_server_previous, GestureActionCategory.SERVERS),
    OPEN_APP_SETTINGS(R.string.gestures_action_open_app_settings, GestureActionCategory.APP),
    OPEN_APP_DEVELOPER(R.string.gestures_action_open_app_developer, GestureActionCategory.APP),
}

enum class GestureActionCategory(@StringRes val description: Int) {
    NONE(R.string.none),
    APP(R.string.gestures_category_app),
    FRONTEND(R.string.app_name),
    NAVIGATION(R.string.gestures_category_navigation),
    SERVERS(R.string.gestures_category_servers),
}

enum class GestureDirection(@StringRes val description: Int) {
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

/** Number of pointers used in a gesture */
enum class GesturePointers(val asInt: Int, @StringRes val description: Int) {
    TWO(asInt = 2, description = R.string.gestures_pointers_two),
    THREE(asInt = 3, description = R.string.gestures_pointers_three),
}

/**
 * Enum with all supported gestures by the app. Currently, a gesture
 * is always a combination of swipe + a direction and pointer count.
 */
@Keep
enum class HAGesture(
    @StringRes val fullDescription: Int,
    val direction: GestureDirection,
    val pointers: GesturePointers,
) {
    SWIPE_LEFT_TWO(
        fullDescription = R.string.gesture_name_swipe_left_two,
        direction = GestureDirection.LEFT,
        pointers = GesturePointers.TWO,
    ),
    SWIPE_LEFT_THREE(
        fullDescription = R.string.gesture_name_swipe_left_three,
        direction = GestureDirection.LEFT,
        pointers = GesturePointers.THREE,
    ),
    SWIPE_RIGHT_TWO(
        fullDescription = R.string.gesture_name_swipe_right_two,
        direction = GestureDirection.RIGHT,
        pointers = GesturePointers.TWO,
    ),
    SWIPE_RIGHT_THREE(
        fullDescription = R.string.gesture_name_swipe_right_three,
        direction = GestureDirection.RIGHT,
        pointers = GesturePointers.THREE,
    ),
    SWIPE_UP_TWO(
        fullDescription = R.string.gesture_name_swipe_up_two,
        direction = GestureDirection.UP,
        pointers = GesturePointers.TWO,
    ),
    SWIPE_UP_THREE(
        fullDescription = R.string.gesture_name_swipe_up_three,
        direction = GestureDirection.UP,
        pointers = GesturePointers.THREE,
    ),
    SWIPE_DOWN_TWO(
        fullDescription = R.string.gesture_name_swipe_down_two,
        direction = GestureDirection.DOWN,
        pointers = GesturePointers.TWO,
    ),
    SWIPE_DOWN_THREE(
        fullDescription = R.string.gesture_name_swipe_down_three,
        direction = GestureDirection.DOWN,
        pointers = GesturePointers.THREE,
    ),
    ;

    companion object {
        /** @return [HAGesture] for a detected swipe if supported, or `null` otherwise */
        fun fromSwipeListener(direction: GestureDirection, pointers: Int): HAGesture? =
            entries.firstOrNull { it.direction == direction && it.pointers.asInt == pointers }
    }
}
