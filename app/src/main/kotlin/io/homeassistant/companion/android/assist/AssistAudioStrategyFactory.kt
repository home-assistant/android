package io.homeassistant.companion.android.assist

import android.content.Context
import androidx.core.content.getSystemService
import io.homeassistant.companion.android.assist.service.AssistVoiceInteractionService
import io.homeassistant.companion.android.assist.wakeword.WakeWordListenerFactory
import io.homeassistant.companion.android.common.assist.AssistAudioStrategy
import io.homeassistant.companion.android.common.assist.DefaultAssistAudioStrategy
import io.homeassistant.companion.android.common.util.VoiceAudioRecorder
import io.homeassistant.companion.android.settings.assist.AssistConfigManager
import javax.inject.Inject

/**
 * Creates [AssistAudioStrategy] instances based on whether wake word detection is needed.
 *
 * Encapsulates the strategy construction logic.
 */
class AssistAudioStrategyFactory @Inject constructor(
    private val voiceAudioRecorder: VoiceAudioRecorder,
    private val wakeWordListenerFactory: WakeWordListenerFactory,
    private val assistConfigManager: AssistConfigManager,
) {

    /**
     * Creates an [AssistAudioStrategy] based on the requested mode.
     *
     * When [wakeWordPhrase] is not null, creates a [WakeWordAssistAudioStrategy] that listens for
     * wake words before starting the Assist pipeline. The strategy's `onListenerStopped` callback
     * automatically resumes the background wake word service via
     * [AssistVoiceInteractionService.resumeListening].
     *
     * When [wakeWordPhrase] is null, creates a [DefaultAssistAudioStrategy] that streams audio
     * directly to the pipeline with audio focus management.
     *
     * @param context Used to obtain the system [android.media.AudioManager] and to resume the
     *   background wake word listener when the strategy stops
     * @param wakeWordPhrase Wake word phrase from an external source (e.g. Intent extra). When
     *   provided, a [WakeWordAssistAudioStrategy] is created and the phrase is used to resolve
     *   the matching wake word model from available models
     * @return The configured [AssistAudioStrategy]
     */
    fun create(context: Context, wakeWordPhrase: String?): AssistAudioStrategy =
        if (wakeWordPhrase != null) {
            WakeWordAssistAudioStrategy(
                voiceAudioRecorder = voiceAudioRecorder,
                wakeWordListenerFactory = wakeWordListenerFactory,
                assistConfigManager = assistConfigManager,
                wakeWordPhrase = wakeWordPhrase,
                onListenerStopped = {
                    AssistVoiceInteractionService.resumeListening(context)
                },
            )
        } else {
            DefaultAssistAudioStrategy(
                voiceAudioRecorder = voiceAudioRecorder,
                audioManager = context.getSystemService(),
            )
        }
}
