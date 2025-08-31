package io.homeassistant.companion.android.common.util

import android.media.AudioAttributes
import android.media.AudioManager
import android.media.AudioManager.OnAudioFocusChangeListener
import android.media.AudioManager.STREAM_MUSIC
import android.media.MediaPlayer
import android.os.Build
import androidx.annotation.VisibleForTesting
import androidx.media.AudioAttributesCompat
import androidx.media.AudioFocusRequestCompat
import androidx.media.AudioManagerCompat
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Simple interface for playing short streaming audio (from URLs).
 */
class AudioUrlPlayer @VisibleForTesting constructor(
    private val audioManager: AudioManager?,
    private val mediaPlayerCreator: () -> MediaPlayer,
) {

    constructor(audioManager: AudioManager?) : this(audioManager, { MediaPlayer() })

    @VisibleForTesting
    var player: MediaPlayer? = null

    @VisibleForTesting
    var focusRequest: AudioFocusRequestCompat? = null
    private val focusListener = OnAudioFocusChangeListener { /* Not used */ }

    /**
     * Stream and play audio from the provided [url]. Any currently playing audio will be stopped.
     * This function will suspend until playback has started.
     * If the current volume of the [STREAM_MUSIC] is 0 it doesn't play the audio and directly return false.
     * @param isAssistant whether the usage/stream should be set to Assistant on supported versions
     * @return `true` if the audio playback started, or `false` if not
     */
    suspend fun playAudio(url: String, isAssistant: Boolean = true): Boolean = withContext(Dispatchers.IO) {
        if (player != null) {
            stop()
        }

        return@withContext if (canPlayMusic()) {
            suspendCancellableCoroutine { cont ->
                player = mediaPlayerCreator().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setContentType(
                                if (isAssistant) {
                                    AudioAttributes.CONTENT_TYPE_SPEECH
                                } else {
                                    AudioAttributes.CONTENT_TYPE_MUSIC
                                },
                            )
                            .setUsage(
                                if (isAssistant && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    AudioAttributes.USAGE_ASSISTANT
                                } else {
                                    AudioAttributes.USAGE_MEDIA
                                },
                            )
                            .build(),
                    )
                    setOnPreparedListener {
                        if (cont.isActive) {
                            requestFocus(isAssistant)
                            it.start()
                        } else {
                            releasePlayer()
                        }
                    }
                    setOnErrorListener { _, what, extra ->
                        Timber.e("Media player encountered error: $what ($extra)")
                        releasePlayer()
                        if (cont.isActive) {
                            cont.resume(false)
                        }
                        return@setOnErrorListener true
                    }
                    setOnCompletionListener {
                        releasePlayer()
                        if (cont.isActive) {
                            cont.resume(true)
                        }
                    }
                }
                cont.invokeOnCancellation {
                    Timber.d("Coroutine cancelled, releasing player")
                    releasePlayer()
                }

                try {
                    player?.setDataSource(url)
                    player?.prepareAsync()
                } catch (e: Exception) {
                    Timber.e(e, "Media player couldn't be prepared")
                    releasePlayer()
                    if (cont.isActive) {
                        cont.resume(false)
                    }
                }
            }
        } else {
            false
        }
    }

    fun stop() {
        try {
            player?.stop()
        } catch (e: IllegalStateException) {
            // Player wasn't initialized, ignore
        }
        releasePlayer()
    }

    private fun canPlayMusic(): Boolean {
        return try {
            audioManager?.getStreamVolume(STREAM_MUSIC) != 0
        } catch (e: RuntimeException) {
            Timber.e(e, "Couldn't get stream volume")
            true
        }
    }

    private fun releasePlayer() {
        player?.release()
        player = null
        abandonFocus()
    }

    private fun requestFocus(isAssistant: Boolean) {
        if (audioManager == null) return
        if (focusRequest == null) {
            focusRequest = AudioFocusRequestCompat.Builder(AudioManagerCompat.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK).run {
                setAudioAttributes(
                    AudioAttributesCompat.Builder().run {
                        if (isAssistant) {
                            setUsage(AudioAttributesCompat.USAGE_ASSISTANT)
                            setContentType(AudioAttributesCompat.CONTENT_TYPE_SPEECH)
                        } else {
                            setUsage(AudioAttributesCompat.USAGE_MEDIA)
                            setContentType(AudioAttributesCompat.CONTENT_TYPE_MUSIC)
                        }
                        build()
                    },
                )
                setOnAudioFocusChangeListener(focusListener)
                build()
            }
        }

        focusRequest?.let {
            try {
                AudioManagerCompat.requestAudioFocus(audioManager, it)
            } catch (e: Exception) {
                // We don't use the result / focus if available but if not still continue
            }
        }
    }

    private fun abandonFocus() {
        if (audioManager == null || focusRequest == null) return
        AudioManagerCompat.abandonAudioFocusRequest(audioManager, focusRequest!!)
    }
}
