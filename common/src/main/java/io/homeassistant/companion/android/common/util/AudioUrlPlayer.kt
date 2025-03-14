package io.homeassistant.companion.android.common.util

import android.media.AudioAttributes
import android.media.AudioManager
import android.media.AudioManager.OnAudioFocusChangeListener
import android.media.MediaPlayer
import android.os.Build
import android.util.Log
import androidx.media.AudioAttributesCompat
import androidx.media.AudioFocusRequestCompat
import androidx.media.AudioManagerCompat
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

/**
 * Simple interface for playing short streaming audio (from URLs).
 */
class AudioUrlPlayer(private val audioManager: AudioManager?) {

    companion object {
        private const val TAG = "AudioUrlPlayer"
    }

    private var player: MediaPlayer? = null

    private var focusRequest: AudioFocusRequestCompat? = null
    private val focusListener = OnAudioFocusChangeListener { /* Not used */ }

    /**
     * Stream and play audio from the provided [url]. Any currently playing audio will be stopped.
     * This function will suspend until playback has started.
     * @param isAssistant whether the usage/stream should be set to Assistant on supported versions
     * @param donePlaying callback to be invoked when playback has finished (it covers when it's not playing anything or an error happened)
     * @return `true` if the audio playback started, or `false` if not
     */
    suspend fun playAudio(url: String, isAssistant: Boolean = true, donePlaying: (() -> Unit)?): Boolean = withContext(Dispatchers.IO) {
        if (player != null) {
            stop()
        }

        val refDonePlaying = AtomicReference<(() -> Unit)?>(donePlaying)

        fun donePlayingInvocation() {
            refDonePlaying.getAndSet(null)?.invoke()
        }

        return@withContext suspendCoroutine { cont ->
            player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(
                            if (isAssistant) AudioAttributes.CONTENT_TYPE_SPEECH else AudioAttributes.CONTENT_TYPE_MUSIC
                        )
                        .setUsage(
                            if (isAssistant && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                AudioAttributes.USAGE_ASSISTANT
                            } else {
                                AudioAttributes.USAGE_MEDIA
                            }
                        )
                        .build()
                )
                setOnPreparedListener {
                    if (isActive) {
                        requestFocus(isAssistant)
                        it.start()
                        cont.resume(true)
                    } else {
                        releasePlayer()
                        cont.resume(false)
                    }
                }
                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "Media player encountered error: $what ($extra)")
                    releasePlayer()
                    cont.resume(false)
                    donePlayingInvocation()
                    return@setOnErrorListener true
                }
                setOnCompletionListener {
                    releasePlayer()
                    donePlayingInvocation()
                }
            }
            try {
                player?.setDataSource(url)
                player?.prepareAsync()
            } catch (e: Exception) {
                Log.e(TAG, "Media player couldn't be prepared", e)
                cont.resume(false)
                donePlayingInvocation()
            }
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
                    }
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