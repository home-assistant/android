package io.homeassistant.companion.android.player

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
@UnstableApi
@Composable
fun rememberBufferingState(player: Player): BufferingState {
    val bufferingState = remember(player) { BufferingState(player) }
    LaunchedEffect(player) { bufferingState.observe() }
    return bufferingState
}

private fun isBuffering(player: Player): Boolean {
    return player.playbackState == Player.STATE_BUFFERING
}

class BufferingState(private val player: Player) {
    var isBuffering by mutableStateOf(isBuffering(player))
        private set

    suspend fun observe(): Nothing =
        player.listen { events ->
            if (
                events.containsAny(
                    Player.EVENT_PLAYBACK_STATE_CHANGED,
                    Player.EVENT_AVAILABLE_COMMANDS_CHANGED
                )
            ) {
                isBuffering = isBuffering(this)
            }
        }
}
