package io.homeassistant.companion.android.assist.wakeword

import android.Manifest
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Listens for wake words using microWakeWord TFLite models.
 *
 * This class handles the complete lifecycle of wake word detection:
 * - TFLite runtime initialization
 * - Model loading
 * - Audio recording
 * - Wake word detection
 * - Resource cleanup
 *
 * **Thread Safety:** This class is thread-safe. [start] and [stop] can be called from any thread.
 *
 * @param context Android context for loading assets and accessing audio
 * @param onListenerReady Callback invoked when initialization completes and listening begins
 * @param onWakeWordDetected Callback invoked when a wake word is detected
 * @param onListenerStopped Callback invoked when the listener stops (normally or due to error)
 */
class WakeWordListener(
    private val context: Context,
    private val onListenerReady: (MicroWakeWordModel) -> Unit = {},
    private val onWakeWordDetected: (MicroWakeWordModel) -> Unit,
    private val onListenerStopped: () -> Unit = {},
    private val tfLiteInitializer: TfLiteInitializer = TfLiteInitializerImpl(),
    private val microWakeWordFactory: suspend (
        MicroWakeWordModel,
    ) -> MicroWakeWord = { modelConfig -> MicroWakeWord.create(context, modelConfig) },
    private val audioRecordFactory: () -> AudioRecord = { createDefaultAudioRecord() },
) {
    private var detectionJob: Job? = null

    /**
     * Whether the listener is currently active.
     */
    val isListening: Boolean
        get() = detectionJob != null

    /**
     * Start listening for wake words.
     *
     * This method initializes the TFLite runtime, loads the first available wake word model,
     * and starts processing audio from the microphone. If already listening, this is a no-op.
     *
     * Requires RECORD_AUDIO permission to be granted.
     */
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun start(coroutineScope: CoroutineScope, modelConfig: MicroWakeWordModel) {
        if (detectionJob != null) {
            Timber.d("WakeWordListener already running")
            return
        }

        detectionJob = coroutineScope.launch {
            // Run detection on background thread to avoid blocking Main
            withContext(Dispatchers.Default) {
                var detector: MicroWakeWord? = null
                var recorder: AudioRecord? = null

                try {
                    detector = initializeDetector(modelConfig)
                    recorder = audioRecordFactory()
                    recorder.startRecording()

                    onListenerReady(modelConfig)

                    runDetectionLoop(modelConfig, detector, recorder)
                } finally {
                    cleanupResources(detector, recorder)
                }
            }
        }.apply {
            invokeOnCompletion { cause ->
                detectionJob = null
                onListenerStopped()
                if (cause != null && cause !is CancellationException) {
                    Timber.w(cause, "DetectionJob completed with exception")
                } else {
                    Timber.d("DetectionJob completed normally")
                }
            }
        }
    }

    /**
     * Stop listening for wake words.
     *
     * This cancels the detection job and cleans up all resources.
     */
    fun stop() {
        Timber.d("Stopping WakeWordListener")
        detectionJob?.cancel()
    }

    private suspend fun initializeDetector(modelConfig: MicroWakeWordModel): MicroWakeWord {
        tfLiteInitializer.initialize(context)

        val detector = microWakeWordFactory(modelConfig)
        Timber.d("MicroWakeWord detector initialized with '$modelConfig'")

        return detector
    }

    private fun CoroutineScope.runDetectionLoop(
        modelConfig: MicroWakeWordModel,
        detector: MicroWakeWord,
        recorder: AudioRecord,
    ) {
        val buffer = ShortArray(CHUNK_SIZE_SAMPLES)

        while (isActive) {
            val readResult = recorder.read(buffer, 0, buffer.size)

            when {
                readResult > 0 -> processAudioChunk(modelConfig, detector, buffer, readResult)
                readResult < 0 -> {
                    Timber.e("AudioRecord read error: $readResult")
                    break
                }
            }
        }
    }

    private fun processAudioChunk(
        modelConfig: MicroWakeWordModel,
        detector: MicroWakeWord,
        buffer: ShortArray,
        readResult: Int,
    ) {
        val detected = detector.processAudio(buffer.copyOf(readResult))
        if (!detected) return

        Timber.i("Wake word detected!")

        // Reset detector state to prevent immediate re-triggering
        detector.reset()
        onWakeWordDetected(modelConfig)
    }

    private fun cleanupResources(detector: MicroWakeWord?, recorder: AudioRecord?) {
        Timber.d("Cleaning up resources")

        recorder?.apply {
            try {
                stop()
                release()
            } catch (e: IllegalStateException) {
                Timber.e(e, "Error stopping AudioRecord")
            }
        }

        detector?.close()
    }

    companion object {
        private const val SAMPLE_RATE = 16000
        private const val CHUNK_SIZE_SAMPLES = 160 // 10ms at 16kHz

        private fun createDefaultAudioRecord(): AudioRecord {
            val channelConfig = AudioFormat.CHANNEL_IN_MONO
            val audioFormat = AudioFormat.ENCODING_PCM_16BIT
            val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, channelConfig, audioFormat)

            if (bufferSize == AudioRecord.ERROR_BAD_VALUE || bufferSize == AudioRecord.ERROR) {
                throw IllegalStateException("Invalid buffer size for AudioRecord: $bufferSize")
            }

            // Use a buffer size that's a multiple of our chunk size (160 samples = 10ms)
            val adjustedBufferSize = maxOf(bufferSize, CHUNK_SIZE_SAMPLES * 4)

            val recorder = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                channelConfig,
                audioFormat,
                adjustedBufferSize,
            )

            if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                recorder.release()
                throw IllegalStateException("AudioRecord failed to initialize")
            }

            return recorder
        }
    }
}
