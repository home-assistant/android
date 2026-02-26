package io.homeassistant.companion.android.common.assist

import android.media.AudioManager
import androidx.media.AudioAttributesCompat
import androidx.media.AudioFocusRequestCompat
import androidx.media.AudioManagerCompat
import io.homeassistant.companion.android.common.util.VoiceAudioRecorder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import timber.log.Timber

/**
 * Abstracts the audio source for the Assist pipeline.
 */
interface AssistAudioStrategy {

    /**
     * Returns the shared flow emitting raw audio samples for the pipeline.
     *
     * Multiple callers receive the same underlying audio stream — only one
     * [android.media.AudioRecord] is active at a time. Collecting the returned flow
     * starts audio capture (if not already active); when all collectors cancel,
     * recording stops and the [android.media.AudioRecord] is released.
     */
    suspend fun audioData(): Flow<ShortArray>

    /**
     * Flow that emits the detected wake word phrase each time a wake word is detected.
     *
     * For strategies without wake word detection (e.g. [DefaultAssistAudioStrategy]),
     * this flow never emits. Collecting it starts the detection lifecycle; cancelling
     * the collection stops detection and releases resources.
     */
    val wakeWordDetected: Flow<String>

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
 * Default strategy. Audio is recorded when [audioData] has a collector,
 * otherwise it doesn't record anything.
 *
 * Audio focus is requested when [requestFocus] is called and abandoned when
 * [abandonFocus] is called.
 */
class DefaultAssistAudioStrategy(
    private val voiceAudioRecorder: VoiceAudioRecorder,
    private val audioManager: AudioManager? = null,
) : AssistAudioStrategy {

    override suspend fun audioData(): Flow<ShortArray> = voiceAudioRecorder.audioData()

    override val wakeWordDetected: Flow<String> = emptyFlow()

    private var focusRequest: AudioFocusRequestCompat? = null
    private val focusListener = AudioManager.OnAudioFocusChangeListener { /* Not used */ }

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

    override fun abandonFocus() {
        if (audioManager == null || focusRequest == null) return
        AudioManagerCompat.abandonAudioFocusRequest(audioManager, focusRequest!!)
    }
}
