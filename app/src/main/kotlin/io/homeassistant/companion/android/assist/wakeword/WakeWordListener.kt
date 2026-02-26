package io.homeassistant.companion.android.assist.wakeword

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import androidx.annotation.RequiresPermission
import androidx.annotation.VisibleForTesting
import io.homeassistant.companion.android.common.util.VoiceAudioRecorder
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber

/** Number of audio chunks to skip wake word processing after a detection (2s at 10ms/chunk). */
@VisibleForTesting
internal const val POST_DETECTION_COOLDOWN_CHUNKS = 200

/**
 * Listens for wake words using microWakeWord TFLite models.
 *
 * This class handles the complete lifecycle of wake word detection:
 * - TFLite runtime initialization
 * - Model loading
 * - Audio recording (delegated to [VoiceAudioRecorder])
 * - Wake word detection
 * - Resource cleanup
 *
 * Audio is obtained by collecting [VoiceAudioRecorder.audioData]. The recorder manages
 * its own [android.media.AudioRecord] lifecycle: collecting starts recording, and
 * cancelling the collection stops recording and releases the device.
 *
 * **Thread Safety:** This class is thread-safe. [start] and [stop] can be called from any thread.
 *
 * @param context Android context for loading assets
 * @param voiceAudioRecorder Provides the audio stream for wake word detection
 * @param onListenerReady Callback invoked when initialization completes and listening begins
 * @param onWakeWordDetected Callback invoked when a wake word is detected
 * @param onListenerStopped Callback invoked when the listener stops (normally or due to error)
 */
@SuppressLint("MissingPermission")
class WakeWordListener(
    private val context: Context,
    private val voiceAudioRecorder: VoiceAudioRecorder,
    private val onWakeWordDetected: (MicroWakeWordModelConfig) -> Unit,
    private val onListenerReady: (MicroWakeWordModelConfig) -> Unit = {},
    private val onListenerStopped: () -> Unit = {},
    private val tfLiteInitializer: TfLiteInitializer = TfLiteInitializerImpl(),
    private val microWakeWordFactory: suspend (
        MicroWakeWordModelConfig,
    ) -> MicroWakeWord = { modelConfig -> MicroWakeWord.create(context, modelConfig) },
) {
    private val detectionMutex = Mutex()
    private var detectionJob: Job? = null

    /**
     * Whether the listener is currently active.
     */
    val isListening: Boolean
        get() = detectionJob?.isActive == true

    /**
     * Start listening for wake words.
     *
     * This method initializes the TFLite runtime, loads the first available wake word model,
     * and starts processing audio from [voiceAudioRecorder]. If already listening, the existing
     * detection is cancelled before starting the new one (to support model changes).
     *
     * Requires RECORD_AUDIO permission to be granted.
     */
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    suspend fun start(
        coroutineScope: CoroutineScope,
        modelConfig: MicroWakeWordModelConfig,
        coroutineContext: CoroutineContext = Dispatchers.Default,
    ) {
        detectionMutex.withLock {
            detectionJob?.cancel()
            detectionJob = coroutineScope.launch {
                // Run detection on background thread to avoid blocking Main
                withContext(coroutineContext) {
                    var microWakeWord: MicroWakeWord? = null

                    try {
                        microWakeWord = initializeMicroWakeWord(modelConfig)

                        onListenerReady(modelConfig)

                        runDetectionLoop(modelConfig, microWakeWord)
                    } finally {
                        microWakeWord?.close()
                    }
                }
            }.apply {
                invokeOnCompletion { cause ->
                    onListenerStopped()
                    if (cause != null && cause !is CancellationException) {
                        Timber.w(cause, "DetectionJob completed with exception")
                    } else {
                        Timber.d("DetectionJob completed normally")
                    }
                }
            }
        }
    }

    /**
     * Stop listening for wake words.
     *
     * This cancels the detection job and cleans up all resources (both [MicroWakeWord]
     * and [android.media.AudioRecord] via [VoiceAudioRecorder] flow cancellation).
     * If [start] is currently initializing (loading TFLite), this suspends on
     * [detectionMutex] until initialization completes, then cancels the job.
     */
    suspend fun stop() {
        detectionMutex.withLock {
            Timber.d("Stopping WakeWordListener")
            detectionJob?.cancel()
        }
    }

    private suspend fun initializeMicroWakeWord(modelConfig: MicroWakeWordModelConfig): MicroWakeWord {
        tfLiteInitializer.initialize(context)

        val microWakeWord = microWakeWordFactory(modelConfig)
        Timber.d("MicroWakeWord initialized with '$modelConfig'")

        return microWakeWord
    }

    private suspend fun runDetectionLoop(
        modelConfig: MicroWakeWordModelConfig,
        microWakeWord: MicroWakeWord,
    ) {
        var cooldownChunksRemaining = 0

        voiceAudioRecorder.audioData().collect { buffer ->
            if (cooldownChunksRemaining > 0) {
                cooldownChunksRemaining--
            } else {
                val detected = processAudioChunk(modelConfig, microWakeWord, buffer)
                if (detected) {
                    cooldownChunksRemaining = POST_DETECTION_COOLDOWN_CHUNKS
                }
            }
        }
    }

    /**
     * @return `true` if the wake word was detected in this chunk
     */
    private fun processAudioChunk(
        modelConfig: MicroWakeWordModelConfig,
        microWakeWord: MicroWakeWord,
        buffer: ShortArray,
    ): Boolean {
        val detected = microWakeWord.processAudio(buffer)
        if (!detected) return false

        // Reset microWakeWord state to prevent immediate re-triggering
        microWakeWord.reset()
        onWakeWordDetected(modelConfig)
        return true
    }
}
