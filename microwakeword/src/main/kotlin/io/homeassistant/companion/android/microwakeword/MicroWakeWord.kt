package io.homeassistant.companion.android.microwakeword

import java.io.Closeable
import java.nio.ByteBuffer
import timber.log.Timber

/**
 * Wake word detector combining audio feature extraction, TFLite Micro inference,
 * and sliding window detection — all in a single C++ engine.
 *
 * Audio must be 16-bit PCM mono at 16 kHz. Other sample rates will produce
 * incorrect feature extraction and unreliable detection.
 *
 * **Thread Safety**: This class is NOT thread-safe. Each instance maintains
 * internal state, so each thread should use its own instance.
 *
 * Usage:
 * ```kotlin
 * val modelBuffer = loadModelAsDirectByteBuffer()
 * MicroWakeWord(
 *     modelBuffer = modelBuffer,
 *     featureStepSizeMs = 20,
 *     probabilityCutoff = 0.5f,
 *     slidingWindowSize = 10,
 * ).use { detector ->
 *     while (recording) {
 *         val samples = readAudioChunk() // 16-bit PCM at 16kHz
 *         if (detector.processAudio(samples)) {
 *             // Wake word detected
 *             detector.reset()
 *         }
 *     }
 * }
 * ```
 *
 * @param modelBuffer   Direct ByteBuffer containing the TFLite flatbuffer model
 * @param featureStepSizeMs  Step size for feature extraction in milliseconds
 * @param probabilityCutoff  Detection threshold (0.0–1.0)
 * @param slidingWindowSize  Number of inference frames to average for detection
 */
class MicroWakeWord(
    modelBuffer: ByteBuffer,
    featureStepSizeMs: Int,
    probabilityCutoff: Float,
    slidingWindowSize: Int,
) : Closeable {

    private var nativeHandle: Long = 0

    init {
        require(featureStepSizeMs > 0) { "featureStepSizeMs must be positive, was $featureStepSizeMs" }
        require(slidingWindowSize > 0) { "slidingWindowSize must be positive, was $slidingWindowSize" }
        require(probabilityCutoff in 0f..1f) { "probabilityCutoff must be in [0.0, 1.0], was $probabilityCutoff" }
        require(modelBuffer.isDirect) { "modelBuffer must be a direct ByteBuffer for JNI access" }
        ensureLibraryLoaded()
        nativeHandle =
            nativeCreate(modelBuffer, DEFAULT_SAMPLE_RATE, featureStepSizeMs, probabilityCutoff, slidingWindowSize)
        if (nativeHandle == 0L) {
            throw IllegalStateException("Failed to create native MicroWakeWord engine")
        }
        Timber.d("MicroWakeWord engine created with handle: $nativeHandle")
    }

    /**
     * Feed audio samples and check for wake word detection.
     *
     * Each call accumulates internal state (feature frames, sliding window probabilities),
     * so audio chunks should be fed sequentially from a continuous stream.
     *
     * @param samples 16-bit PCM mono audio samples at 16 kHz
     * @return true if wake word was detected in this or recent frames
     */
    fun processAudio(samples: ShortArray): Boolean {
        check(nativeHandle != 0L) { "MicroWakeWord has been closed" }
        return nativeProcessAudio(nativeHandle, samples)
    }

    /**
     * Reset all internal state (frontend, feature buffer, detection state).
     *
     * Call this after a detection to prevent immediate re-triggering,
     * or when switching to a new audio stream.
     */
    fun reset() {
        check(nativeHandle != 0L) { "MicroWakeWord has been closed" }
        nativeReset(nativeHandle)
        Timber.d("MicroWakeWord reset")
    }

    /**
     * Release native resources. After this call, the instance cannot be used.
     * Safe to call multiple times.
     */
    override fun close() {
        if (nativeHandle != 0L) {
            Timber.d("Closing MicroWakeWord engine with handle: $nativeHandle")
            nativeDestroy(nativeHandle)
            nativeHandle = 0
        }
    }

    /**
     * Safety net to release native resources if [close] was not called.
     * Prefer calling [close] explicitly or using a `use` block.
     */
    protected fun finalize() {
        close()
    }

    private companion object {
        const val DEFAULT_SAMPLE_RATE = 16000

        /**
         * Lazily loads the native library when the first [MicroWakeWord] instance is created.
         *
         * Using lazy loading instead of a companion `init` block allows test frameworks
         * (e.g., MockK) to create mocks of this class without triggering native library
         * loading.
         */
        private val libraryLoaded: Unit by lazy {
            System.loadLibrary("microwakeword")
            Timber.d("Loaded microwakeword native library")
        }

        fun ensureLibraryLoaded() {
            libraryLoaded
        }

        @JvmStatic
        external fun nativeCreate(
            modelBuffer: ByteBuffer,
            sampleRate: Int,
            featureStepSizeMs: Int,
            probabilityCutoff: Float,
            slidingWindowSize: Int,
        ): Long

        @JvmStatic
        external fun nativeProcessAudio(handle: Long, samples: ShortArray): Boolean

        @JvmStatic
        external fun nativeReset(handle: Long)

        @JvmStatic
        external fun nativeDestroy(handle: Long)
    }
}
