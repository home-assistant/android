package io.homeassistant.companion.android.assist

import android.annotation.SuppressLint
import io.homeassistant.companion.android.assist.wakeword.MicroWakeWordModelConfig
import io.homeassistant.companion.android.assist.wakeword.WakeWordListenerFactory
import io.homeassistant.companion.android.common.assist.AssistAudioStrategy
import io.homeassistant.companion.android.common.util.VoiceAudioRecorder
import io.homeassistant.companion.android.settings.assist.AssistConfigManager
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import timber.log.Timber

/**
 * Audio strategy that uses a shared [VoiceAudioRecorder] for both the Assist pipeline
 * and wake word detection.
 *
 * The same [voiceAudioRecorder] instance is shared with the [WakeWordListener][io.homeassistant.companion.android.assist.wakeword.WakeWordListener]
 * (via the [WakeWordListenerFactory]). [VoiceAudioRecorder.audioData] returns a shared flow
 * backed by a single [android.media.AudioRecord], so both the wake word listener and the
 * pipeline can collect concurrently without creating multiple recorder instances.
 *
 * The [wakeWordDetected] flow controls the listener's lifecycle: collecting it starts
 * the listener, and cancelling the collection stops it and releases all resources.
 *
 * @param voiceAudioRecorder Shared voice recorder
 * @param wakeWordListenerFactory Factory to create the wake word listener
 * @param assistConfigManager Assist configuration manager
 * @param wakeWordPhrase Wake word phrase from an external source.
 *   When provided, the model whose [MicroWakeWordModelConfig.wakeWord] matches this phrase is
 *   used for detection.
 * @param onListenerStopped Called when the listener is fully stopped. Callers can use
 *   this to resume other audio operations (e.g. a background wake word service).
 */
class WakeWordAssistAudioStrategy(
    private val voiceAudioRecorder: VoiceAudioRecorder,
    wakeWordListenerFactory: WakeWordListenerFactory,
    private val assistConfigManager: AssistConfigManager,
    private val wakeWordPhrase: String,
    onListenerStopped: () -> Unit = {},
) : AssistAudioStrategy {

    private val wakeWordChannel = Channel<String>(Channel.CONFLATED)

    private val listener by lazy {
        wakeWordListenerFactory.create(
            onWakeWordDetected = { modelConfig ->
                wakeWordChannel.trySend(modelConfig.wakeWord)
            },
            onListenerStopped = onListenerStopped,
        )
    }

    override suspend fun audioData(): Flow<ShortArray> = voiceAudioRecorder.audioData()

    /**
     * Cold flow that starts the wake word listener when collected.
     *
     * The collector's [kotlinx.coroutines.CoroutineScope] (from [callbackFlow]) is used as
     * the listener's scope — when the collection is canceled.
     */
    @SuppressLint("MissingPermission")
    override val wakeWordDetected: Flow<String> = callbackFlow {
        val model = resolveModelFromPhrase()

        listener.start(coroutineScope = this, modelConfig = model)

        wakeWordChannel.consumeEach {
            trySend(it)
        }
    }

    /** No-op */
    override fun requestFocus() {}

    /** No-op */
    override fun abandonFocus() {}

    /**
     * Resolves the [MicroWakeWordModelConfig] to use for wake word detection.
     *
     * When [wakeWordPhrase] is set, searches available models for a matching
     * [MicroWakeWordModelConfig.wakeWord]. If no match is found or no phrase was provided,
     * falls back to the first available model.
     */
    private suspend fun resolveModelFromPhrase(): MicroWakeWordModelConfig {
        val model = assistConfigManager.getAvailableModels().find { it.wakeWord == wakeWordPhrase }
        return if (model != null) {
            Timber.d("Resolved wake word model from phrase: '${model.wakeWord}'")
            model
        } else {
            val firstModel = assistConfigManager.getAvailableModels().first()
            Timber.w("Could not resolve wake word model for phrase: '$wakeWordPhrase' falling back to $firstModel")
            firstModel
        }
    }
}
