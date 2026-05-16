package io.homeassistant.companion.android.frontend.exoplayer

import android.content.Context
import android.net.Uri
import androidx.annotation.VisibleForTesting
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.ExoPlayer
import dagger.hilt.android.qualifiers.ApplicationContext
import io.homeassistant.companion.android.common.util.initializePlayer
import io.homeassistant.companion.android.frontend.handler.FrontendHandlerEvent
import java.io.Closeable
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import timber.log.Timber

/**
 * Manages the ExoPlayer lifecycle for streams requested by the Home Assistant frontend.
 *
 * The frontend sends `exoplayer/play_hls`, `exoplayer/resize`, and `exoplayer/stop` messages
 * which are forwarded here as [FrontendHandlerEvent.ExoPlayerAction] instances. This manager
 * owns the [Player] instance and exposes a [state] flow.
 *
 * The player need to be released by calling [close] (typically in ViewModel's `onCleared`).
 */
class FrontendExoPlayerManager @VisibleForTesting constructor(
    private val playerCreator: suspend (ExoPlayer.() -> Unit) -> ExoPlayer,
) : Closeable {

    @Inject
    constructor(@ApplicationContext context: Context, dataSourceFactory: DataSource.Factory) : this(
        { configure ->
            initializePlayer(context, dataSourceFactory).apply(configure)
        },
    )

    private var player: ExoPlayer? = null

    private val _state = MutableStateFlow<ExoPlayerUiState?>(null)
    val state: StateFlow<ExoPlayerUiState?> = _state.asStateFlow()

    /**
     * Handles a [FrontendHandlerEvent.ExoPlayerAction] from the external bus.
     */
    suspend fun handle(message: FrontendHandlerEvent.ExoPlayerAction) {
        when (message) {
            is FrontendHandlerEvent.ExoPlayerAction.PlayHls -> {
                playHls(url = message.url, muted = message.muted)
            }

            is FrontendHandlerEvent.ExoPlayerAction.Stop -> close()
            is FrontendHandlerEvent.ExoPlayerAction.Resize -> {
                resize(left = message.left, top = message.top, right = message.right, bottom = message.bottom)
            }
        }
    }

    private suspend fun playHls(url: Uri, muted: Boolean) {
        val exoPlayer = getOrCreatePlayer()
        exoPlayer.stop()
        exoPlayer.setMediaItem(MediaItem.fromUri(url))
        exoPlayer.playWhenReady = true
        exoPlayer.volume = if (muted) 0f else 1f
        exoPlayer.prepare()
        _state.value = ExoPlayerUiState(player = exoPlayer)
    }

    private suspend fun getOrCreatePlayer(): ExoPlayer {
        player?.let { return it }
        return playerCreator {
            addListener(VideoSizeListener())
        }.also { player = it }
    }

    private fun resize(left: Double, top: Double, right: Double, bottom: Double) {
        Timber.d("resize left=$left top=$top right=$right bottom=$bottom")
        _state.update { currentState ->
            if (currentState == null) return@update null

            val width = right - left
            // If the frontend didn't impose a real height (bottom equals or precedes top),
            // compute one from the video's aspect ratio if we know it.
            val height = if (bottom > top) {
                bottom - top
            } else {
                currentState.videoAspectRatio?.let { ratio -> width * ratio } ?: 0.0
            }

            currentState.copy(
                left = left.dp,
                top = top.dp,
                size = DpSize(width.dp, height.coerceAtLeast(0.0).dp),
            )
        }
    }

    /**
     * Toggles fullscreen state for the player.
     */
    fun onFullscreenChanged(isFullScreen: Boolean) {
        _state.update { it?.copy(isFullScreen = isFullScreen) }
    }

    /**
     * Releases the player and clears state. Safe to call multiple times.
     */
    override fun close() {
        player?.release()
        player = null
        _state.value = null
    }

    /**
     * Listener that caches the video's native aspect ratio on the UI state and auto-calculates
     * the player height when the frontend has not imposed a real height (e.g. zero-height DOMRect).
     */
    private inner class VideoSizeListener : Player.Listener {
        override fun onVideoSizeChanged(videoSize: VideoSize) {
            if (videoSize.height == 0 || videoSize.width == 0) return
            val ratio = videoSize.height.toDouble() / videoSize.width
            Timber.d("onVideoSizeChanged ${videoSize.width}x${videoSize.height} (ratio=$ratio)")
            _state.update { currentState ->
                if (currentState == null) return@update null
                // Store the ratio for later zero-height resizes.
                val withRatio = currentState.copy(videoAspectRatio = ratio)
                // Also auto-size now if the current height was not imposed by the frontend.
                val size = withRatio.size ?: return@update withRatio
                if (size.height > 0.dp) return@update withRatio
                withRatio.copy(size = DpSize(size.width, size.width * ratio.toFloat()))
            }
        }
    }
}
