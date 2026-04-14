package io.homeassistant.companion.android.common.assist

import android.media.AudioManager
import androidx.media.AudioAttributesCompat
import androidx.media.AudioFocusRequestCompat
import androidx.media.AudioManagerCompat
import timber.log.Timber

interface AssistAudioFocus {

    /**
     * Request audio focus for the pipeline.
     */
    fun requestFocus()

    /**
     * Abandon audio focus previously requested via [requestFocus].
     */
    fun abandonFocus()
}

/**
 * Manages transient-exclusive audio focus for the Assist pipeline.
 *
 * Requests focus with [AudioAttributesCompat.USAGE_ASSISTANT] so that media players
 * duck or pause while the assistant is active. Call [requestFocus] when the pipeline
 * starts recording and [abandonFocus] when the pipeline completes.
 *
 * @param audioManager System audio manager. When `null`, both methods are no-ops,
 *   which allows callers to gracefully handle a missing audio service.
 */
class AssistAudioFocusImpl(private val audioManager: AudioManager?) : AssistAudioFocus {

    private var focusRequest: AudioFocusRequestCompat? = null
    private val focusListener = AudioManager.OnAudioFocusChangeListener { /* Not used */ }

    /**
     * Requests transient-exclusive audio focus for the Assist pipeline.
     *
     * The first call lazily creates the [AudioFocusRequestCompat]; subsequent calls
     * reuse the same request. Does nothing when [audioManager] is `null`.
     */
    override fun requestFocus() {
        if (audioManager == null) return
        if (focusRequest == null) {
            focusRequest = AudioFocusRequestCompat.Builder(AudioManagerCompat.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE).run {
                setAudioAttributes(
                    AudioAttributesCompat.Builder().run {
                        setUsage(AudioAttributesCompat.USAGE_ASSISTANT)
                        setContentType(AudioAttributesCompat.CONTENT_TYPE_SPEECH)
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
                Timber.w(e, "Failed to request audio focus")
            }
        }
    }

    /**
     * Abandons audio focus previously requested via [requestFocus].
     *
     * Does nothing when [audioManager] is `null` or [requestFocus] was never called.
     */
    override fun abandonFocus() {
        if (audioManager == null || focusRequest == null) return
        AudioManagerCompat.abandonAudioFocusRequest(audioManager, focusRequest!!)
    }
}
