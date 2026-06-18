package io.homeassistant.companion.android.assist.wakeword

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import androidx.annotation.RequiresPermission
import androidx.annotation.VisibleForTesting
import io.homeassistant.companion.android.common.util.VoiceAudioRecorder
import io.homeassistant.companion.android.microwakeword.MicroWakeWord
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
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
 * - Model loading
 * - Audio recording (delegated to [VoiceAudioRecorder])
 * - Wake word detection (via [MicroWakeWord] native engine)
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
 * @param onListenerReady Callback invoked when initialization completes and listening begins.
 *        Called on the background dispatcher passed to [start] (defaults to [Dispatchers.Default]).
 * @param onWakeWordDetected Callback invoked when a wake word is detected.
 *        Called on the background dispatcher passed to [start] (defaults to [Dispatchers.Default]).
 * @param onListenerStopped Callback invoked when the listener stops (normally or due to error).
 *        Called on the dispatcher of the coroutine scope passed to [start].
 */
@SuppressLint("MissingPermission")
class WakeWordListener(
    context: Context,
    private val voiceAudioRecorder: VoiceAudioRecorder,
    private val onWakeWordDetected: (MicroWakeWordModelConfig) -> Unit,
    private val onListenerReady: (MicroWakeWordModelConfig) -> Unit = {},
    private val onListenerStopped: () -> Unit = {},
    private val microWakeWordFactory: suspend (
        MicroWakeWordModelConfig,
    ) -> MicroWakeWord = { modelConfig -> createMicroWakeWord(context, modelConfig) },
) {
    private val detectionMutex = Mutex()
    private var detectionJob: Job? = null

    /**
     * Start listening for wake words.
     *
     * This method loads the wake word model, and starts processing audio from
     * [voiceAudioRecorder]. If already listening, the existing detection is cancelled
     * before starting the new one (to support model changes).
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
                    microWakeWordFactory(modelConfig).use { microWakeWord ->
                        onListenerReady(modelConfig)
                        runDetectionLoop(modelConfig, microWakeWord)
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
     * If [start] is currently initializing, this suspends on [detectionMutex] until
     * initialization completes, then cancels the job.
     */
    suspend fun stop() {
        detectionMutex.withLock {
            Timber.d("Stopping WakeWordListener")
            detectionJob?.cancel()
        }
    }

    private suspend fun runDetectionLoop(modelConfig: MicroWakeWordModelConfig, microWakeWord: MicroWakeWord) {
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

/**
 * Create a [MicroWakeWord] instance from a model config by loading the model from assets.
 */
private suspend fun createMicroWakeWord(context: Context, modelConfig: MicroWakeWordModelConfig): MicroWakeWord =
    withContext(Dispatchers.IO) {
        Timber.d("Loading wake word model: ${modelConfig.wakeWord} (${modelConfig.modelAssetPath})")
        val modelBuffer = loadModelFile(context, modelConfig.modelAssetPath)
        MicroWakeWord(
            modelBuffer = modelBuffer,
            featureStepSizeMs = modelConfig.micro.featureStepSize,
            probabilityCutoff = modelConfig.micro.probabilityCutoff,
            slidingWindowSize = modelConfig.micro.slidingWindowSize,
        )
    }

/**
 * Load a model file from assets into a direct ByteBuffer suitable for JNI.
 */
private fun loadModelFile(context: Context, assetPath: String): ByteBuffer {
    val assetFileDescriptor = context.assets.openFd(assetPath)
    val mappedBuffer = assetFileDescriptor.use { fd ->
        fd.createInputStream().use { inputStream ->
            val fileChannel = inputStream.channel
            fileChannel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
        }
    }
    // Ensure native byte order for direct buffer passed to JNI
    mappedBuffer.order(ByteOrder.nativeOrder())
    return mappedBuffer
}
