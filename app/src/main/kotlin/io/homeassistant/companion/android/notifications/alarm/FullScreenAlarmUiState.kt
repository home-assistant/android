package io.homeassistant.companion.android.notifications.alarm

import androidx.compose.ui.graphics.ImageBitmap
import io.github.timoptr.mdiicons.MdiIcon

/**
 * State of the full-screen alarm, built from the posted notification it belongs to.
 *
 * @property icon icon shown at the top of the screen, from `alarm_icon` or the notification icon
 * @property title title of the notification
 * @property message message of the notification
 * @property canOpen whether the notification has a target to open, like a dashboard or a camera view
 * @property actions actions of the notification that can be used from the alarm screen
 * @property isFullScreenIntentAllowed whether the user allows the app to show full-screen alerts, which Android 14
 *  and later require to show the alarm screen on its own when the device is locked
 * @property image picture of the notification, set with its `image` option, like a camera snapshot
 */
data class FullScreenAlarmUiState(
    val icon: MdiIcon,
    val title: String,
    val message: String,
    val canOpen: Boolean,
    val actions: List<FullScreenAlarmAction>,
    val isFullScreenIntentAllowed: Boolean,
    val image: ImageBitmap? = null,
)

/**
 * Action of the notification shown on the alarm screen.
 *
 * @property index position of the action in the notification actions
 * @property title label of the action
 */
data class FullScreenAlarmAction(val index: Int, val title: String)
