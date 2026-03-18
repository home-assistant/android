package io.homeassistant.companion.android.microwakeword

import android.os.Build
import java.io.Closeable
import java.nio.ByteBuffer
import timber.log.Timber

/** ABIs for which the native `libmicrowakeword.so` is bundled. */
private val SUPPORTED_ABIS = setOf("arm64-v8a", "x86_64")

/**
 * Checks whether the current device supports the MicroWakeWord native library.
 *
 * The native library is only built for 64-bit architectures (arm64-v8a and x86_64).
 * Devices running 32-bit ABIs (armeabi-v7a, x86) cannot use MicroWakeWord.
 *
 * @return true if at least one of the device's supported ABIs has a bundled native library
 */
val isMicroWakeWordSupported: Boolean by lazy { Build.SUPPORTED_ABIS.any { it in SUPPORTED_ABIS } }

/**
 * Wake word detector combining audio feature extraction, TFLite Micro inference,
 * and sliding window detection — all in a single C++ engine.
 *
 * This replaces the previous split architecture where feature extraction was in C++
 * and model inference + detection state were in Kotlin with a Gradle TFLite dependency.
 * By running everything natively via TFLite Micro, no Play Services or bundled LiteRT
 * runtime is needed.
 *
 * **Thread Safety**: This class is NOT thread-safe. Each instance maintains
 * internal state, so each thread should use its own instance.
 *
 * Callers should check [isMicroWakeWordSupported] before constructing an instance.
 *
 * @param modelBuffer   Direct ByteBuffer containing the TFLite flatbuffer model
 * @param featureStepSizeMs  Step size for feature extraction in milliseconds
 * @param probabilityCutoff  Detection threshold (0.0–1.0)
 * @param slidingWindowSize  Number of inference frames to average for detection
 * @param sampleRate    Audio sample rate in Hz (default 16000)
 */
class MicroWakeWord(
    modelBuffer: ByteBuffer,
    featureStepSizeMs: Int,
    probabilityCutoff: Float,
    slidingWindowSize: Int,
    sampleRate: Int = DEFAULT_SAMPLE_RATE,
) : Closeable {

    private var nativeHandle: Long = 0

    init {
        ensureLibraryLoaded()
        nativeHandle = nativeCreate(modelBuffer, sampleRate, featureStepSizeMs, probabilityCutoff, slidingWindowSize)
        if (nativeHandle == 0L) {
            throw IllegalStateException("Failed to create native MicroWakeWord engine")
        }
        Timber.d("MicroWakeWord engine created with handle: $nativeHandle")
    }

    /**
     * Process audio samples and check for wake word detection.
     *
     * @param samples 16-bit PCM audio samples at the configured sample rate
     * @return true if wake word was detected
     */
    fun processAudio(samples: ShortArray): Boolean {
        check(nativeHandle != 0L) { "MicroWakeWord has been closed" }
        return nativeProcessAudio(nativeHandle, samples)
    }

    /**
     * Reset all internal state (frontend, feature buffer, detection state).
     * Call this after a detection or when starting a new audio stream.
     */
    fun reset() {
        check(nativeHandle != 0L) { "MicroWakeWord has been closed" }
        nativeReset(nativeHandle)
        Timber.d("MicroWakeWord engine reset")
    }

    override fun close() {
        if (nativeHandle != 0L) {
            Timber.d("Closing MicroWakeWord engine with handle: $nativeHandle")
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

        /**
         * Lazily loads the native library when the first [MicroWakeWord] instance is created.
         *
         * Using lazy loading instead of a companion `init` block allows test frameworks
         * (e.g., MockK) to create mocks of this class without triggering native library
         * loading.
         */
        private val libraryLoaded: Unit by lazy {
            check(isMicroWakeWordSupported) {
                "MicroWakeWord native library is not available for this device's ABIs: " +
                    "${Build.SUPPORTED_ABIS.toList()}. " +
                    "Callers must check isMicroWakeWordSupported before using MicroWakeWord"
            }
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
