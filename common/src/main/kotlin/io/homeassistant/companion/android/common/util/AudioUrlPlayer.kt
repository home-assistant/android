package io.homeassistant.companion.android.common.util

import android.content.Context
import android.media.AudioManager
import android.media.AudioManager.STREAM_MUSIC
import androidx.annotation.OptIn
import androidx.annotation.VisibleForTesting
import androidx.media.AudioAttributesCompat
import androidx.media.AudioFocusRequestCompat
import androidx.media.AudioManagerCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import timber.log.Timber

/**
 * Represents the playback state of the audio player.
 */
enum class PlaybackState {
    /** Player is ready to play but hasn't started yet. */
    READY,

    /** Player is actively playing audio. */
    PLAYING,

    /** Player stopped playing - either finished, stream ended, or encountered an error. */
    STOP_PLAYING,
}

/**
 * Simple interface for playing streaming audio from URLs.
 */
class AudioUrlPlayer @VisibleForTesting constructor(
    private val audioManager: AudioManager?,
    private val playerCreator: suspend (ExoPlayer.() -> Unit) -> ExoPlayer,
) {

    constructor(context: Context, audioManager: AudioManager?, dataSourceFactory: DataSource.Factory) : this(
        audioManager,
        {
            val player = initializePlayer(context, dataSourceFactory)
            player.apply(it)
        },
    )

    /**
     * Streams and plays audio from the provided [url].
     *
     * The player starts when the Flow is collected. The flow ensures that all interactions with the
     * player happen on the main thread.
     *
     * The Flow emits [PlaybackState] changes and completes if an error occurs or the upstream ends.
     * If the [audioManager] is null or the current volume of [STREAM_MUSIC] is 0,
     * the Flow completes immediately without playing.
     *
     * The player is properly released once the flow is canceled.
     *
     * @param url the URL to stream audio from
     * @param isAssistant whether the usage/stream should be set to Assistant on supported versions
     * @return a Flow that emits [PlaybackState] changes
     */
    @OptIn(UnstableApi::class)
    fun playAudio(url: String, isAssistant: Boolean = true): Flow<PlaybackState> = callbackFlow {
        if (!canPlayMusic()) {
            close()
            return@callbackFlow
        }
        var request: AudioFocusRequestCompat? = null

        val player = playerCreator {
            setAudioAttributes(
                buildAudioAttributes(isAssistant),
                false, // handleAudioFocus doesn't support USAGE_ASSISTANT
            )

            var hasStartedPlayback = false

            addListener(
                object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        when (playbackState) {
                            Player.STATE_READY -> {
                                if (!hasStartedPlayback) {
                                    hasStartedPlayback = true
                                    trySend(PlaybackState.READY)
                                    request = requestFocus(isAssistant)
                                    play()
                                    trySend(PlaybackState.PLAYING)
                                }
                            }

                            Player.STATE_BUFFERING -> {
                                if (hasStartedPlayback) {
                                    Timber.d("Stream buffering - no data currently available")
                                }
                            }

                            Player.STATE_ENDED -> {
                                Timber.d("Stream playback ended")
                                trySend(PlaybackState.STOP_PLAYING)
                            }

                            Player.STATE_IDLE -> {
                                // Player is idle, nothing to do
                            }
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        val isStreamEnded = hasEofException(error)
                        if (isStreamEnded) {
                            Timber.d("Stream ended - no more data from server")
                            trySend(PlaybackState.STOP_PLAYING)
                        } else {
                            Timber.e(error, "ExoPlayer encountered error")
                        }
                        releasePlayer(this@playerCreator, request)
                        close()
                    }
                },
            )

            setMediaItem(MediaItem.fromUri(url))
            prepare()
        }

        awaitClose {
            Timber.d("Flow closed, releasing player")
            releasePlayer(player, request)
        }
    }.flowOn(Dispatchers.Main)

    private fun canPlayMusic(): Boolean {
        return try {
            audioManager != null && audioManager.getStreamVolume(STREAM_MUSIC) != 0
        } catch (e: RuntimeException) {
            Timber.e(e, "Couldn't get stream volume")
            true
        }
    }

    private fun buildAudioAttributes(isAssistant: Boolean): AudioAttributes = AudioAttributes.Builder()
        .setContentType(if (isAssistant) C.AUDIO_CONTENT_TYPE_SPEECH else C.AUDIO_CONTENT_TYPE_MUSIC)
        .setUsage(if (isAssistant) C.USAGE_ASSISTANT else C.USAGE_MEDIA)
        .build()

    private fun releasePlayer(player: Player, request: AudioFocusRequestCompat?) {
        player.release()
        abandonFocus(request)
    }

    private fun requestFocus(isAssistant: Boolean): AudioFocusRequestCompat? {
        if (audioManager == null) return null
        val request = AudioFocusRequestCompat.Builder(AudioManagerCompat.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(buildAudioAttributesCompat(isAssistant))
            .setOnAudioFocusChangeListener { /* Focus changes are ignored */ }
            .build()

        return try {
            AudioManagerCompat.requestAudioFocus(audioManager, request)
            request
        } catch (e: Exception) {
            // Focus request failed, continue without audio focus
            Timber.w(e, "Failed to request focus")
            null
        }
    }

    private fun buildAudioAttributesCompat(isAssistant: Boolean): AudioAttributesCompat =
        AudioAttributesCompat.Builder()
            .setUsage(if (isAssistant) AudioAttributesCompat.USAGE_ASSISTANT else AudioAttributesCompat.USAGE_MEDIA)
            .setContentType(
                if (isAssistant) {
                    AudioAttributesCompat.CONTENT_TYPE_SPEECH
                } else {
                    AudioAttributesCompat.CONTENT_TYPE_MUSIC
                },
            )
            .build()

    private fun abandonFocus(requestCompat: AudioFocusRequestCompat?) {
        if (audioManager == null || requestCompat == null) return
        AudioManagerCompat.abandonAudioFocusRequest(audioManager, requestCompat)
    }

    /**
     * Checks if the exception chain contains an EOFException, indicating the stream ended.
     */
    private fun hasEofException(error: Throwable?): Boolean =
        generateSequence(error) { it.cause }.any { it is java.io.EOFException }
}
