package io.homeassistant.companion.android.common.assist

import android.media.AudioManager
import io.homeassistant.companion.android.common.util.VoiceAudioRecorder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Abstracts the audio source for the Assist pipeline.
 */
interface AssistAudioStrategy : AssistAudioFocus {

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
    audioManager: AudioManager? = null,
) : AssistAudioStrategy,
    AssistAudioFocus by AssistAudioFocusImpl(audioManager) {

    override suspend fun audioData(): Flow<ShortArray> = voiceAudioRecorder.audioData()

    override val wakeWordDetected: Flow<String> = emptyFlow()
}
