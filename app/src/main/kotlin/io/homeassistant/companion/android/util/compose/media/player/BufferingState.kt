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
fun rememberBufferingState(player: Player): BufferingState {
    val bufferingState = remember(player) { BufferingState(player) }
    LaunchedEffect(player) { bufferingState.observe() }
    return bufferingState
}

private fun isBuffering(player: Player): Boolean {
    return player.playbackState == Player.STATE_BUFFERING
}

/**
 * State that converts the necessary information from the [Player] to correctly deal with a UI
 * component representing the buffering.
 *
 * @property[isBuffering] determined by [Player.getPlaybackState] equals to [Player.STATE_BUFFERING]
 */
@OptIn(UnstableApi::class)
class BufferingState(private val player: Player) {
    var isBuffering by mutableStateOf(isBuffering(player))
        private set

    /**
     * Subscribes to updates from [Player.Events] and listens to
     * * [Player.EVENT_PLAYBACK_STATE_CHANGED] in order to
     *   determine whether the player is buffering.
     */
    suspend fun observe(): Nothing = player.listen { events ->
        if (
            events.containsAny(
                Player.EVENT_PLAYBACK_STATE_CHANGED,
            )
        ) {
            isBuffering = isBuffering(this)
        }
    }
}
