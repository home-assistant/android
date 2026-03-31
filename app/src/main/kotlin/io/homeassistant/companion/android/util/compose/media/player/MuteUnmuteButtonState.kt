package io.homeassistant.companion.android.util.compose.media.player

import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.media3.common.Player
import androidx.media3.common.listen
import androidx.media3.common.util.UnstableApi

/**
 * Remembers the value of [MuteUnmuteButtonState] created based on the passed [Player] and launch a
 * coroutine to listen to [Player's][Player] changes. If the [Player] instance changes between
 * compositions, produce and remember a new value.
 */
@Composable
fun rememberMuteUnmuteButtonState(player: Player): MuteUnmuteButtonState {
    val muteUnmuteButtonState = remember(player) { MuteUnmuteButtonState(player) }
    LaunchedEffect(player) { muteUnmuteButtonState.observe() }
    return muteUnmuteButtonState
}

private fun shouldEnableMuteUnmuteButton(player: Player): Boolean {
    return player.isCommandAvailable(Player.COMMAND_SET_VOLUME)
}

private fun shouldShowMuteUnmuteButton(player: Player): Boolean {
    return player.volume == 0.0f
}

/**
 * State that converts the necessary information from the [Player] to correctly deal with a UI
 * component representing a MuteUnmute button.
 *
 * @property[isEnabled] determined by `isCommandAvailable(Player.COMMAND_SET_VOLUME)`
 * @property[showMute] determined by [Player.getVolume] equals 0
 */
@OptIn(UnstableApi::class)
class MuteUnmuteButtonState(private val player: Player) {
    var isEnabled by mutableStateOf(shouldEnableMuteUnmuteButton(player))
        private set

    var showMute by mutableStateOf(shouldShowMuteUnmuteButton(player))
        private set

    /**
     * Handles the interaction with the MuteUnmute button according to the current state of the
     * [Player].
     *
     * If the volume is set to 0 then it will set it to 1 (unmute) otherwise it will set to 0 (mute).
     */
    fun onClick() {
        player.volume = if (player.volume == 0.0f) 1.0f else 0.0f
    }

    /**
     * Subscribes to updates from [Player.Events] and listens to
     * * [Player.EVENT_DEVICE_VOLUME_CHANGED] and [Player.EVENT_VOLUME_CHANGED] in order to
     *   determine whether a mute or unmute button should be presented on a UI element for playback
     *   control.
     * * [Player.EVENT_AVAILABLE_COMMANDS_CHANGED] in order to determine whether the button should be
     *   enabled, i.e. respond to user input.
     */
    suspend fun observe(): Nothing = player.listen { events ->
        if (
            events.containsAny(
                Player.EVENT_DEVICE_VOLUME_CHANGED,
                Player.EVENT_VOLUME_CHANGED,
                Player.EVENT_AVAILABLE_COMMANDS_CHANGED,
            )
        ) {
            showMute = shouldShowMuteUnmuteButton(this)
            isEnabled = shouldEnableMuteUnmuteButton(this)
        }
    }
}
