package io.homeassistant.companion.android.common.util

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Simple interface for playing short streaming audio (from URLs).
 */
class AudioUrlPlayer {

    companion object {
        private const val TAG = "AudioUrlPlayer"
    }

    private var player: MediaPlayer? = null

    /**
     * Stream and play audio from the provided [url]. Any currently playing audio will be stopped.
     * This function will suspend until playback has started.
     * @param isAssistant whether the usage/stream should be set to Assistant on supported versions
     * @return `true` if the audio playback started, or `false` if not
     */
    suspend fun playAudio(url: String, isAssistant: Boolean = true): Boolean = withContext(Dispatchers.IO) {
        if (player != null) {
            stop()
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
                    return@setOnErrorListener true
                }
                setOnCompletionListener {
                    releasePlayer()
                }
            }
            try {
                player?.setDataSource(url)
                player?.prepareAsync()
            } catch (e: Exception) {
                Log.e(TAG, "Media player couldn't be prepared", e)
                cont.resume(false)
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
    }
}
