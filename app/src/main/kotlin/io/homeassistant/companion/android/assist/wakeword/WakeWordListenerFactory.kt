package io.homeassistant.companion.android.assist.wakeword

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.homeassistant.companion.android.common.util.VoiceAudioRecorder
import javax.inject.Inject

/**
 * Factory for creating [WakeWordListener] instances.
 *
 * [WakeWordListener] requires an [android.content.Context] and a [VoiceAudioRecorder].
 * This factory holds both dependencies via Hilt, so callers only need
 * to supply the event callbacks relevant to their use case.
 */
class WakeWordListenerFactory @Inject constructor(
    @ApplicationContext private val context: Context,
    private val voiceAudioRecorder: VoiceAudioRecorder,
) {
    /**
     * Creates a new [WakeWordListener] with the given callbacks.
     *
     * @param onWakeWordDetected Called when a wake word is detected, with the detected model config
     * @param onListenerReady Called when initialization completes and listening begins
     * @param onListenerStopped Called when the listener stops (normally or due to error)
     */
    fun create(
        onWakeWordDetected: (MicroWakeWordModelConfig) -> Unit,
        onListenerReady: (MicroWakeWordModelConfig) -> Unit = {},
        onListenerStopped: () -> Unit = {},
    ): WakeWordListener {
        return WakeWordListener(
            context = context,
            voiceAudioRecorder = voiceAudioRecorder,
            onWakeWordDetected = onWakeWordDetected,
            onListenerReady = onListenerReady,
            onListenerStopped = onListenerStopped,
        )
    }
}
