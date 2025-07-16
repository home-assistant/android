package io.homeassistant.companion.android.common.util.tts

import android.media.AudioAttributes
import android.media.AudioManager

object TextToSpeechData {
    const val TTS = "TTS"
    const val TTS_TEXT = "tts_text"

    const val COMMAND_STOP_TTS = "command_stop_tts"
}

/**
 * Interface for a text to speech engine.
 */
interface TextToSpeechEngine {

    /**
     * Suspends until the engine is initialized.
     *
     * If already initialized, a successful [Result] returns immediately.
     *
     * @return success or initialization error [Throwable]
     */
    suspend fun initialize(): Result<Unit>

    /**
     * Suspends until the engine finishes the playback.
     *
     * @return success or playback error [Throwable]
     */
    suspend fun play(utterance: Utterance): Result<Unit>

    /**
     * Stops all playback and releases engines resources.
     */
    fun release()
}

/**
 * Data model for an utterance to be played.
 *
 * @param id a unique identifier
 * @param text message to be synthesized
 * @param streamVolumeAdjustment utility object to adjust the volume ahead of this utterance's playback,
 * and reset it back after it's finished
 * @param audioAttributes attributes to be set for the media player responsible for the audio playback
 */
data class Utterance(
    val id: String,
    val text: String,
    val streamVolumeAdjustment: StreamVolumeAdjustment,
    val audioAttributes: AudioAttributes,
)

/**
 * Utility object to adjust the volume ahead of this utterance's playback, and reset it back after it's finished.
 */
sealed class StreamVolumeAdjustment {

    /**
     * Applies volume adjustment.
     */
    abstract fun overrideVolume()

    /**
     * Resets the volume back to pre-adjustment levels. Does nothing if [overrideVolume] wasn't called before.
     */
    abstract fun resetVolume()

    /**
     * Object that does no adjustments to audio stream's volume level.
     */
    data object None : StreamVolumeAdjustment() {
        override fun overrideVolume() {
            // no-op
        }

        override fun resetVolume() {
            // no-op
        }
    }

    /**
     * Object that maximizes the volume of a specific [streamId].
     */
    class Maximize(private val audioManager: AudioManager, private val streamId: Int) : StreamVolumeAdjustment() {
        private val maxVolume: Int = audioManager.getStreamMaxVolume(streamId)
        private var resetVolume: Int? = null

        override fun overrideVolume() {
            resetVolume = audioManager.getStreamVolume(streamId)
            audioManager.setStreamVolume(streamId, maxVolume, 0)
        }

        override fun resetVolume() {
            resetVolume?.let { volume ->
                audioManager.setStreamVolume(streamId, volume, 0)
            }
            resetVolume = null
        }
    }
}
