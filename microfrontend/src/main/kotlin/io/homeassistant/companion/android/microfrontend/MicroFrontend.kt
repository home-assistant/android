package io.homeassistant.companion.android.microfrontend

import timber.log.Timber

/**
 * JNI wrapper for TFLite Micro Frontend audio feature extraction.
 *
 * This class provides fixed-point 16-bit feature extraction that matches
 * the ESPHome microWakeWord implementation, ensuring compatibility with
 * models trained using the TFLite Micro Frontend.
 *
 * The native implementation uses hardcoded settings that match ESPHome
 * [preprocessor_settings.h](https://github.com/esphome/esphome/blob/8d1379a2752291d2f4e33d5831d51c2afd59f7ce/esphome/components/micro_wake_word/preprocessor_settings.h):
 * - 40 mel filterbank bins
 * - 125-7500 Hz frequency range
 * - 30ms window size
 * - PCAN strength 0.95
 *
 * **Thread Safety**: This class is NOT thread-safe. Each instance maintains
 * internal state for noise reduction and PCAN, so each thread should
 * use its own instance.
 *
 * Thanks to [brownard](https://github.com/brownard/Ava) for the inspiration on the native implementation.
 *
 * @param stepSizeMs Step size between frames in milliseconds
 * @param sampleRate Audio sample rate in Hz
 * @see FeatureExtractor
 */
class MicroFrontend(private val stepSizeMs: Int, private val sampleRate: Int = DEFAULT_SAMPLE_RATE) : FeatureExtractor {

    private var nativeHandle: Long = 0

    init {
        nativeHandle = nativeCreate(sampleRate, stepSizeMs)
        if (nativeHandle == 0L) {
            throw IllegalStateException("Failed to create native MicroFrontend")
        }
        Timber.d("MicroFrontend created with handle: $nativeHandle (sampleRate=$sampleRate, stepSizeMs=$stepSizeMs)")
    }

    /**
     * Process audio samples and extract spectrogram features.
     *
     * @param samples 16-bit PCM audio samples at the configured sample rate
     * @return List of feature frames, each containing 40 mel filterbank values
     */
    override fun processSamples(samples: ShortArray): List<FloatArray> {
        check(nativeHandle != 0L) { "MicroFrontend has been closed" }
        @Suppress("UNCHECKED_CAST")
        return nativeProcessSamples(nativeHandle, samples) as List<FloatArray>
    }

    /**
     * Reset internal state (noise estimates, PCAN state, sample buffer).
     * Call this when starting a new audio stream.
     */
    override fun reset() {
        check(nativeHandle != 0L) { "MicroFrontend has been closed" }
        nativeReset(nativeHandle)
        Timber.d("MicroFrontend reset")
    }

    override fun close() {
        if (nativeHandle != 0L) {
            Timber.d("Closing MicroFrontend with handle: $nativeHandle")
            nativeDestroy(nativeHandle)
            nativeHandle = 0
        }
    }

    /**
     * Safety net to release native resources if [close] was not called.
     * Prefer calling [close] explicitly when done with this instance.
     */
    protected fun finalize() {
        close()
    }

    private companion object {
        const val DEFAULT_SAMPLE_RATE = 16000

        init {
            System.loadLibrary("microfrontend")
            Timber.d("Loaded microfrontend native library")
        }

        @JvmStatic
        external fun nativeCreate(sampleRate: Int, stepSizeMs: Int): Long

        @JvmStatic
        external fun nativeDestroy(handle: Long)

        @JvmStatic
        external fun nativeProcessSamples(handle: Long, samples: ShortArray): Any

        @JvmStatic
        external fun nativeReset(handle: Long)
    }
}
