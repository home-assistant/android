package io.homeassistant.companion.android.microfrontend

import java.io.Closeable

/**
 * Interface for audio feature extraction.
 *
 * Implementations extract spectrogram features from audio samples for use
 * in wake word detection models.
 */
interface FeatureExtractor : Closeable {

    /**
     * Process audio samples and extract spectrogram features.
     *
     * @param samples 16-bit PCM audio samples
     * @return List of feature frames, each containing mel filterbank bins
     */
    fun processSamples(samples: ShortArray): List<FloatArray>

    /**
     * Reset internal state.
     * Call this when starting a new audio stream.
     */
    fun reset()
}
